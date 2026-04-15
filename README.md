# comas
Decompiled version of CoMaS

CoMaS stores the client encrypted on disk.
It decrypts it to a temp file before launch, and deletes itself when CoMas is exited. By running CoMas, and observing the temp folder seeing which files were created, I was able to find the decrypted version.

Notable behaviours:

- It reads your browser histories (Chrome, Firefox, Edge, Opera, Vivaldi, Brave). This means that tabs accessed on other devices CAN be read if you're using the same account.
- Webcam capture, with optional face, gaze, and gesture detection.
- Clipboard reading and blocking.
- Process and window monitoring, with active control. The client enumerates processes and desktop windows and supports actions like kill, suspend, resume, quit
- File-system monitoring. It watches the desktop, CoMaS folder, course/activity folders, exam/resources/screens/logs/tools/archives, and records “unsanctioned files”.
- Network and environment enforcement. The session monitor checks VPNs, DNS/IP configuration, maximum monitors, removable volumes, virtual displays, and VM-related interfaces, and can be configured to terminate on some of those conditions.
- System inventory/reporting. It gathers hardware, memory, networking, displays, graphics cards, file systems, running apps, processes, and desktop windows.
