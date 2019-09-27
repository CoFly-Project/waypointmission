WaypointMission

how to run the over-the-lan debugger 

1)cd C:\Users\atkap\AppData\Local\Android\Sdk\platform-tools
2).\adb tcpip 5555
3).\adb connect 192.168.43.86:5555 or .\adb connect 192.168.1.13:5555

to stop it
./adb kill-server