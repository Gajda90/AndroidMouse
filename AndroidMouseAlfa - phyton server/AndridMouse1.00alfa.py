import bluetooth
import socket
from pynput.mouse import Controller, Button
import tkinter as tk
from tkinter import scrolledtext
import threading
import queue
import datetime
import traceback
import select
import time


class AndroidMouseServer(threading.Thread):
    def __init__(self, status_queue, log_queue):
        """Inicjalizuje serwer myszy w osobnym wątku."""
        super().__init__()
        self.daemon = True
        self.mouse = Controller()
        self.server_sock = None
        self.client_sock = None
        self.uuid = "00001101-0000-1000-8000-00805F9B34FB"
        self.status_queue = status_queue
        self.log_queue = log_queue
        self._stop_event = threading.Event()

    def _log(self, message):
        """Wysyła wiadomość do logu GUI oraz dopisuje wpis do pliku z timestampem."""
        try:
            ts = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            entry = f"[{ts}] {message}"
            self.log_queue.put(entry)
            with open('andmouse.log', 'a', encoding='utf-8') as f:
                f.write(entry + '\n')
        except Exception as e:
            # Podstawowe logowanie, jeśli główne zawiedzie
            self.log_queue.put(f"[Logging Error] {e}")

    def _update_status(self, status):
        """Aktualizuje status w GUI."""
        self.status_queue.put(status)

    def stop(self):
        """Sygnalizuje zatrzymanie wątku serwera."""
        self._stop_event.set()
        # Delikatne zamknięcie gniazd, aby przerwać blokujące operacje
        if self.server_sock:
            try:
                self.server_sock.close()
            except (OSError, bluetooth.btcommon.BluetoothError):
                pass
        if self.client_sock:
            try:
                self.client_sock.close()
            except (OSError, bluetooth.btcommon.BluetoothError):
                pass

    def run(self):
        """Główna pętla serwera."""
        try:
            self.server_sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
            self.server_sock.bind(("", bluetooth.PORT_ANY))
            self.server_sock.listen(1)
            port = self.server_sock.getsockname()[1]

            try:
                bluetooth.advertise_service(self.server_sock, "AndroidMouseServer",
                                            service_id=self.uuid,
                                            service_classes=[self.uuid, bluetooth.SERIAL_PORT_CLASS],
                                            profiles=[bluetooth.SERIAL_PORT_PROFILE])
            except bluetooth.btcommon.BluetoothError as e:
                self._log(f"Warning: Nie można zarejestrować usługi Bluetooth: {e}")

            self._update_status(f"Czeka na połączenie na porcie {port}")
            self._log(f"Czekam na połączenie na porcie RFCOMM {port}")

            self.server_sock.settimeout(1.0)
            while not self._stop_event.is_set():
                try:
                    self.client_sock, client_info = self.server_sock.accept()
                    peer_name = self._get_peer_name(client_info)
                    self._update_status(f"Połączono z {peer_name}")
                    self._log(f"Zaakceptowano połączenie od {peer_name}")
                    self.client_sock.settimeout(1.0)  # Krótki timeout dla pętli select
                    self._handle_client_session()
                except bluetooth.btcommon.BluetoothError as e:
                    if "timed out" not in str(e).lower() and not self._stop_event.is_set():
                        self._log(f"Błąd serwera Bluetooth: {e}")
                        time.sleep(0.5)
                except Exception as e:
                    if not self._stop_event.is_set():
                        self._log(f"Nieoczekiwany błąd w pętli serwera: {type(e).__name__}: {e}")
                        self._log(traceback.format_exc())
                        time.sleep(0.5)
        except Exception as e:
            if not self._stop_event.is_set():
                self._log(f"Krytyczny błąd serwera: {e}")
                self._log(traceback.format_exc())
                self._update_status("Błąd serwera")
        finally:
            self._cleanup()

    def _get_peer_name(self, client_info):
        """Pobiera nazwę urządzenia klienta."""
        addr = client_info[0]
        try:
            name = bluetooth.lookup_name(addr, timeout=2)
            return f"{name} ({addr})" if name else addr
        except bluetooth.btcommon.BluetoothError:
            return addr

    def _handle_client_session(self):
        """Obsługuje sesję połączonego klienta."""
        try:
            while not self._stop_event.is_set():
                ready, _, _ = select.select([self.client_sock], [], [], 1.0)
                if not ready:
                    continue
                data = self.client_sock.recv(1024)
                if not data:
                    self._log("Połączenie zamknięte przez klienta.")
                    break
                message = data.decode('utf-8', errors='replace').strip()
                self._log(f"Odebrano: '{message}'")
                self._process_command(message)
        except (OSError, bluetooth.btcommon.BluetoothError) as e:
            if not self._stop_event.is_set():
                self._log(f"Błąd połączenia: {e}")
        except Exception as e:
            if not self._stop_event.is_set():
                self._log(f"Błąd w sesji klienta: {type(e).__name__}: {e}")
                self._log(traceback.format_exc())
        finally:
            if self.client_sock:
                try:
                    self.client_sock.close()
                except (OSError, bluetooth.btcommon.BluetoothError):
                    pass
                self.client_sock = None
            self._log("Zakończono sesję klienta.")
            self._update_status("Oczekuje na nowe połączenie")

    def _process_command(self, message):
        """Przetwarza odebrane polecenia."""
        try:
            if message.startswith("MOVE:"):
                parts_str = message.split(':', 1)[1]
                parts = parts_str.split(',')
                if len(parts) == 2:
                    try:
                        dx = float(parts[0])
                        dy = float(parts[1])
                        self.mouse.move(int(dx), int(dy))
                    except ValueError:
                        self._log("BŁĄD: Nieprawidłowe wartości w komendzie MOVE.")
                else:
                    self._log("BŁĄD: Nieprawidłowy format komendy MOVE.")
            elif message == "L_CLICK":
                self.mouse.click(Button.left)
            elif message == "R_CLICK":
                self.mouse.click(Button.right)
        except (ValueError, IndexError) as e:
            self._log(f"BŁĄD podczas interpretacji komendy '{message}': {e}")

    def _cleanup(self):
        """Zamyka wszystkie zasoby."""
        self._log("Zamykanie serwera.")
        if self.client_sock:
            try:
                self.client_sock.close()
            except (OSError, bluetooth.btcommon.BluetoothError):
                pass
        if self.server_sock:
            try:
                self.server_sock.close()
            except (OSError, bluetooth.btcommon.BluetoothError):
                pass
        self.client_sock = None
        self.server_sock = None
        self._update_status("Wyłączony")


class App:
    def __init__(self, root_widget):
        self.root = root_widget
        self.root.title("Android Mouse Server")
        self.root.minsize(400, 500)  # Ustawienie minimalnego rozmiaru okna
        self.server_thread = None
        self.status_queue = queue.Queue()
        self.log_queue = queue.Queue()

        self.status_label = tk.Label(self.root, text="Status: Wyłączony", fg="red", padx=10, pady=10)
        self.status_label.pack()

        self.log_area = scrolledtext.ScrolledText(self.root, wrap=tk.WORD, width=70, height=20)
        self.log_area.pack(padx=10, pady=10, fill="both", expand=True)

        button_frame = tk.Frame(self.root)
        button_frame.pack(padx=10, pady=10)

        self.start_button = tk.Button(button_frame, text="Włącz serwer", command=self.start_server)
        self.start_button.pack(side=tk.LEFT, padx=5)

        self.stop_button = tk.Button(button_frame, text="Wyłącz serwer", command=self.stop_server, state=tk.DISABLED)
        self.stop_button.pack(side=tk.RIGHT, padx=5)

        self.root.protocol("WM_DELETE_WINDOW", self.on_closing)
        self.process_queues()

    def start_server(self):
        self.log_message("Uruchamianie serwera...")
        self.server_thread = AndroidMouseServer(self.status_queue, self.log_queue)
        self.server_thread.start()
        self.start_button.config(state=tk.DISABLED)
        self.stop_button.config(state=tk.NORMAL)
        self.status_label.config(text="Status: Uruchamianie...", fg="orange")

    def stop_server(self):
        if self.server_thread and self.server_thread.is_alive():
            self.log_message("Zatrzymywanie serwera...")
            self.server_thread.stop()
            self.server_thread.join(timeout=2.0)
        self.start_button.config(state=tk.NORMAL)
        self.stop_button.config(state=tk.DISABLED)
        self.status_label.config(text="Status: Wyłączony", fg="red")

    def process_queues(self):
        """Przetwarza kolejki statusu i logów z wątku serwera."""
        try:
            while not self.status_queue.empty():
                status = self.status_queue.get_nowait()
                color = "green" if "Połączono" in status else "orange" if "Czeka" in status else "red"
                self.status_label.config(text=f"Status: {status}", fg=color)
        except queue.Empty:
            pass

        try:
            while not self.log_queue.empty():
                log_msg = self.log_queue.get_nowait()
                self.log_message(log_msg)
        except queue.Empty:
            pass

        self.root.after(100, self.process_queues)

    def log_message(self, message):
        """Dodaje wiadomość do pola logów."""
        self.log_area.configure(state='normal')
        self.log_area.insert(tk.END, message + '\n')
        self.log_area.configure(state='disabled')
        self.log_area.see(tk.END)

    def on_closing(self):
        """Obsługa zamknięcia okna."""
        self.stop_server()
        self.root.destroy()


if __name__ == "__main__":
    main_window = tk.Tk()
    app = App(main_window)
    main_window.mainloop()
