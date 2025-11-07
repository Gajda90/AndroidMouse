package com.example.androidmouse

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

// ZMIANA 1: Dodajemy interfejs (callback), aby informować MainActivity o stanie połączenia.
interface BluetoothStateListener {
    fun onStateChanged(state: String)
    fun onDataSent(data: String) // Callback do wyświetlania wysłanych danych
}

@SuppressLint("MissingPermission") // Zakładamy, że uprawnienia są sprawdzane w MainActivity
class BluetoothController(private val listener: BluetoothStateListener) { // ZMIANA 2: Przyjmujemy listenera w konstruktorze

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null

    // ZMIANA 3: Dodajemy 'synchronized' do kluczowych metod, aby uniknąć problemów z wielowątkowością.
    @Synchronized
    fun connect(device: BluetoothDevice) {
        listener.onStateChanged("Próba połączenia...")
        // Anuluj wszystkie istniejące połączenia
        cancel()

        connectThread = ConnectThread(device)
        connectThread?.start()
    }

    fun write(data: ByteArray) {
        // ZMIANA 4: Tworzymy lokalną, bezpieczną kopię wątku, aby uniknąć NullPointerException.
        val localConnectedThread: ConnectedThread?
        synchronized(this) {
            localConnectedThread = connectedThread
        }
        // Wywołujemy metodę 'write' na tej bezpiecznej kopii.
        localConnectedThread?.write(data)
    }

    @Synchronized
    fun cancel() {
        connectThread?.cancel()
        connectThread = null

        connectedThread?.cancel()
        connectedThread = null

        listener.onStateChanged("Rozłączono.")
    }

    @Synchronized
    private fun manageConnectedSocket(socket: BluetoothSocket) {
        // Zamykamy stary wątek 'connect', bo już go nie potrzebujemy
        connectThread = null

        connectedThread = ConnectedThread(socket)
        connectedThread?.start()

        // Informujemy UI, że połączenie się udało
        listener.onStateChanged("Połączono z ${socket.remoteDevice.name}")
    }

    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        // ZMIANA 5: Inicjalizacja socketu przeniesiona do bloku 'try-catch' dla lepszej obsługi błędów.
        private val mmSocket: BluetoothSocket? = try {
            device.createRfcommSocketToServiceRecord(sppUuid)
        } catch (e: IOException) {
            Log.e("BluetoothController", "Błąd tworzenia socketu", e)
            listener.onStateChanged("Błąd: Nie można utworzyć socketu")
            null
        } catch (e: SecurityException) {
            Log.e("BluetoothController", "Błąd uprawnień przy tworzeniu socketu", e)
            listener.onStateChanged("Błąd: Brak uprawnień do tworzenia socketu")
            null
        }

        override fun run() {
            // Zawsze anuluj discovery, ponieważ spowalnia ono połączenie.
            // BluetoothAdapter jest przestarzały, ale na razie go zostawmy, bo kod na to pozwala
            // BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()

            mmSocket?.let { socket ->
                try {
                    // To jest blokująca operacja. Pozostanie tu, dopóki nie połączy się lub nie wystąpi błąd.
                    socket.connect()
                    // Jeśli doszło tu, to połączenie się udało.
                    manageConnectedSocket(socket)
                } catch (e: IOException) {
                    Log.e("BluetoothController", "Nie można połączyć się z socketem", e)
                    listener.onStateChanged("Błąd: Nie można się połączyć")
                    try {
                        socket.close()
                    } catch (closeException: IOException) {
                        Log.e("BluetoothController", "Nie można zamknąć socketu po błędzie połączenia", closeException)
                    }
                }
            }
        }

        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e("BluetoothController", "Nie można zamknąć socketu w ConnectThread", e)
            }
        }
    }

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {

        private val mmOutStream: OutputStream = mmSocket.outputStream

        fun write(bytes: ByteArray) {
            try {
                mmOutStream.write(bytes)
                // ZMIANA 6: Informujemy UI o tym, co właśnie wysłaliśmy.
                val sentMessage = String(bytes).trim()
                listener.onDataSent(sentMessage)
            } catch (e: IOException) {
                Log.e("BluetoothController", "Błąd podczas zapisu danych", e)
                listener.onStateChanged("Błąd: Utracono połączenie")
            }
        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e("BluetoothController", "Nie można zamknąć socketu w ConnectedThread", e)
            }
        }
    }
}
