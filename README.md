# Android DJI Adaptor #

Android application acting as Ground Control Station for DJI modile SDK compatible drones. 


### Example of waypoint mission visualization
<img src="/art/dji_adaptor_example.gif?raw=true">

### Run the over-the-lan debugger
```
cd %USERPROFILE%\AppData\Local\Android\Sdk\platform-tools
```
```
--> Connect android phone to the PC through usb cable
```
```
.\adb tcpip 5555
```
```
--> Disconnect android phone from the PC and find the ANDROID_IP
```
```
.\adb connect ANDROID_IP:5555
```


Stop over-the-lan debugger:
```
.\adb kill-server
```
### Cite as
```
(TBA)
```
