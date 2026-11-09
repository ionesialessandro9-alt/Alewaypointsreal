import sys
import json
import math
from pathlib import Path

import mss
import win32gui
from PySide6.QtCore import Qt, QTimer, QRectF
from PySide6.QtGui import QImage, QPixmap, QPainter, QPen, QColor, QFont
from PySide6.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QLabel, QPushButton, QComboBox
)

PROFILES = Path.home() / "AppData/Roaming/.dawn/profiles"


def find_live_file():
    candidates = []
    if PROFILES.exists():
        candidates.extend(PROFILES.glob("**/.minecraft/config/alex-waypoints-live.json"))
    direct = Path.home() / "AppData/Roaming/.minecraft/config/alex-waypoints-live.json"
    if direct.exists():
        candidates.insert(0, direct)
    for candidate in candidates:
        try:
            if candidate.is_file():
                return candidate
        except OSError:
            pass
    return None


def minecraft_windows():
    matches = []

    def callback(hwnd, _):
        title = win32gui.GetWindowText(hwnd)
        if not win32gui.IsWindowVisible(hwnd) or not title:
            return
        low = title.lower()
        if any(word in low for word in ("minecraft", "feather", "dawn")):
            try:
                left, top, right, bottom = win32gui.GetClientRect(hwnd)
                if right > 300 and bottom > 200:
                    matches.append((hwnd, title))
            except Exception:
                pass

    win32gui.EnumWindows(callback, None)
    return matches


class GameView(QWidget):
    def __init__(self):
        super().__init__()
        self.setMinimumSize(640, 360)
        self.frame = None
        self.data = None

    def set_frame(self, frame, data):
        self.frame = frame
        self.data = data
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.fillRect(self.rect(), QColor(12, 12, 16))

        if self.frame is None:
            painter.setPen(Qt.white)
            painter.drawText(self.rect(), Qt.AlignCenter, "Apri Minecraft / Dawn Client")
            return

        pixmap = QPixmap.fromImage(self.frame).scaled(
            self.size(), Qt.KeepAspectRatio, Qt.SmoothTransformation
        )
        ox = (self.width() - pixmap.width()) / 2
        oy = (self.height() - pixmap.height()) / 2
        painter.drawPixmap(int(ox), int(oy), pixmap)

        data = self.data
        if not data or data.get("name") == "Nessun waypoint":
            return

        dx = data["x"] - data["playerX"]
        dy = data["y"] - data["playerY"]
        dz = data["z"] - data["playerZ"]
        yaw = math.radians(data.get("yaw", 0))
        pitch = math.radians(data.get("pitch", 0))

        forward = (-math.sin(yaw) * math.cos(pitch), -math.sin(pitch), math.cos(yaw) * math.cos(pitch))
        right = (math.cos(yaw), 0, math.sin(yaw))
        up = (math.sin(yaw) * math.sin(pitch), math.cos(pitch), -math.cos(yaw) * math.sin(pitch))

        distance = math.sqrt(dx * dx + dy * dy + dz * dz)
        if distance < 0.01:
            return

        vector = (dx / distance, dy / distance, dz / distance)
        depth = sum(vector[i] * forward[i] for i in range(3))
        if depth <= 0.02:
            return

        horizontal = sum(vector[i] * right[i] for i in range(3))
        vertical = sum(vector[i] * up[i] for i in range(3))
        focal = 1 / math.tan(math.radians(70) / 2)
        x = ox + pixmap.width() * (0.5 + 0.5 * focal * horizontal / depth)
        y = oy + pixmap.height() * (0.5 - 0.5 * focal * vertical / depth)

        if not (ox - 20 < x < ox + pixmap.width() + 20 and oy - 20 < y < oy + pixmap.height() + 20):
            return

        painter.setRenderHint(QPainter.Antialiasing)
        painter.setPen(QPen(QColor(245, 25, 55), 3))
        painter.drawLine(int(x), int(max(oy, y - 100)), int(x), int(min(oy + pixmap.height(), y + 42)))
        painter.drawEllipse(int(x - 12), int(y - 12), 24, 24)

        label = f'{data["name"]} ({distance:.0f}m)'
        painter.setFont(QFont("Segoe UI", 11, QFont.Bold))
        width = painter.fontMetrics().horizontalAdvance(label) + 20
        rect = QRectF(x - width / 2, y - 48, width, 27)
        painter.setBrush(QColor(10, 10, 15, 225))
        painter.drawRoundedRect(rect, 7, 7)
        painter.setPen(QPen(QColor(245, 25, 55), 2))
        painter.drawRoundedRect(rect, 7, 7)
        painter.setPen(Qt.white)
        painter.drawText(rect, Qt.AlignCenter, label)


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("ALEX WAYPOINTS 3.0")
        self.resize(1100, 720)
        self.view = GameView()
        self.selector = QComboBox()
        self.status = QLabel("ALEX / IN ATTESA DI MINECRAFT")
        self.status.setStyleSheet("color:#ff4058;font-weight:bold;padding:8px")

        refresh = QPushButton("Aggiorna finestre")
        refresh.clicked.connect(self.scan_windows)

        header = QHBoxLayout()
        header.addWidget(self.status, 1)
        header.addWidget(QLabel("Finestra Minecraft:"))
        header.addWidget(self.selector)
        header.addWidget(refresh)

        layout = QVBoxLayout()
        layout.addLayout(header)
        layout.addWidget(self.view, 1)
        central = QWidget()
        central.setLayout(layout)
        self.setCentralWidget(central)
        self.setStyleSheet(
            "QMainWindow,QWidget{background:#111116;color:#eee;font-family:'Segoe UI'}"
            "QPushButton,QComboBox{background:#292932;padding:7px;border:1px solid #555;border-radius:5px}"
        )

        self.scan_windows()
        self.timer = QTimer(self)
        self.timer.timeout.connect(self.update_capture)
        self.timer.start(200)

    def scan_windows(self):
        self.items = minecraft_windows()
        self.selector.clear()
        for hwnd, title in self.items:
            self.selector.addItem(title, hwnd)

    def update_capture(self):
        try:
            live_path = find_live_file()
            data = json.loads(live_path.read_text(encoding="utf-8")) if live_path else None
            hwnd = self.selector.currentData()

            if hwnd and win32gui.IsWindow(hwnd):
                left, top, right, bottom = win32gui.GetClientRect(hwnd)
                screen_x, screen_y = win32gui.ClientToScreen(hwnd, (0, 0))
                if right <= 0 or bottom <= 0:
                    return
                with mss.mss() as capture:
                    shot = capture.grab({
                        "left": screen_x, "top": screen_y,
                        "width": right, "height": bottom
                    })
                image = QImage(
                    shot.rgb, shot.width, shot.height,
                    shot.width * 3, QImage.Format_RGB888
                ).copy()
                self.view.set_frame(image, data)
                if data:
                    self.status.setText("ALEX / COLLEGATO - WAYPOINT LIVE")
                else:
                    self.status.setText("ALEX / MOD O FILE DATI NON TROVATO")
            else:
                self.view.set_frame(None, data)
                self.status.setText("ALEX / MINECRAFT NON TROVATO")
        except Exception as error:
            self.status.setText("ALEX / ERRORE: " + str(error)[:100])


if __name__ == "__main__":
    app = QApplication(sys.argv)
    window = MainWindow()
    window.show()
    sys.exit(app.exec())
