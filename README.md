# comas
Decompiled version of CoMaS 0.8.75 - Invasive proctoring software that does not disclose what it does properly. For educational purposes only!

CoMaS stores the client encrypted on disk.
It decrypts it to a temp file before launch, and deletes itself when CoMas is exited. By running CoMas, and observing the temp folder seeing which files were created, I was able to find the decrypted version. 

Funny that they would encrypt the file but have ZERO obfuscation, especially for Java apps where bytecode is trivially easy to decompile. Whole process to obtain decompiled version was trivial, taking 10 minutes total. #devops

Notable behaviours:

-   It reads your browser histories (Chrome, Firefox, Edge, Opera, Vivaldi, Brave). This means that tabs accessed on other devices CAN be read if you're using the same account.
    - AppData\Local\Google\Chrome\User Data\Default\History for example on Windows devices. It is an sqlite database that stores every single action you have done on Google Chrome. That website you visited 3 years ago? CoMas saw it. The same is true for other browsers
    
    -   This is concerning as shared devices (family members) history would show up through CoMas. What if they open a banking app? Or what if they are opening sensitive websites that they don't want others to see?
        
-   Webcam capture, with optional face, gaze, and gesture detection.
    
    -   not a concern, students must have webcam on and is standard
        
-   Clipboard reading and blocking.
    
    -   also not a concern, just makes sure you don't have anything in your clipboard prior to the exam
        
-   Process and window monitoring, with active control. The client enumerates processes and desktop windows and supports actions like kill, suspend, resume, quit
    
    -   basically checks what apps / processes you have running. A bit invasive, especially if running sensitive programs
        
-   File-system monitoring. It watches the desktop, CoMaS folder, course/activity folders, exam/resources/screens/logs/tools/archives, and records “unsanctioned files”.
    
    -   similar to the previous point. It takes screenshots of your desktop routinely and uploads it to the server. Invasive I suppose but makes sense in the context of a proctoring software
        
-   Network and environment enforcement. The session monitor checks VPNs, DNS/IP configuration, maximum monitors, removable volumes, virtual displays, and VM-related interfaces, and can be configured to terminate on some of those conditions.
    
    -   Checks current websites/connections the desktop is making requests to, and checks for other devices on the local network. Privacy concern
        
-   System inventory/reporting. It gathers hardware, memory, networking, displays, graphics cards, file systems, running apps, processes, and desktop windows.
    
    -   basic system info
