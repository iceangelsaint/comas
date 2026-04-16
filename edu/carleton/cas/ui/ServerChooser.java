package edu.carleton.cas.ui;

import edu.carleton.cas.constants.ClientShared;
import edu.carleton.cas.logging.Logger;
import edu.carleton.cas.utility.ClientConfiguration;
import edu.carleton.cas.utility.IconLoader;
import java.awt.Component;
import java.awt.Frame;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

public class ServerChooser {
   private static boolean USE_ONLY_HOST_BY_DEFAULT = false;
   private ClientConfiguration config;
   private Component component;

   public ServerChooser(Component component) {
      this(component, new ClientConfiguration(ClientShared.COMAS_DIRECTORY + File.separator + ClientShared.COMAS_DOT_INI));
   }

   public ServerChooser(Component component, ClientConfiguration config) {
      this.config = config;
      this.component = component;
   }

   public void open() {
      this.config.load();
   }

   public void close() {
      this.config.save("CoMaS server location information. DO NOT EDIT");
   }

   public String select() {
      String host = this.choose();
      if (host != null) {
         this.config.setRecentHost(host);
         this.config.save("CoMaS server location information. DO NOT EDIT");
      } else {
         this.askToDeleteConfiguration();
      }

      return host;
   }

   private String choose() {
      if (ClientShared.SERVER_CHOSEN != null) {
         return ClientShared.SERVER_CHOSEN;
      } else if (!this.config.hasHost()) {
         return this.inputNewHost();
      } else {
         return USE_ONLY_HOST_BY_DEFAULT && this.config.hasOneHost() ? this.config.getHost() : this.askToSelectHost();
      }
   }

   private void checkComponent() {
      if (this.component == null) {
         Frame alwaysOnTop = new JFrame();
         alwaysOnTop.setAlwaysOnTop(true);
         this.component = alwaysOnTop;
      }

   }

   private String askToSelectHost() {
      Object[] hosts = this.config.getHosts();
      ImageIcon icon = IconLoader.getDefaultIcon();
      this.checkComponent();
      String hostChosen = (String)JOptionPane.showInputDialog(this.component, "Please choose a CoMaS server. You may press Cancel to enter a new one:", "CoMaS Server Choice", -1, icon, hosts, hosts[0]);
      if (hostChosen == null) {
         hostChosen = this.inputNewHost();
      }

      return hostChosen;
   }

   private String inputNewHost() {
      String oldHost = this.config.getHost();
      if (oldHost == null) {
         oldHost = ClientShared.DEFAULT_HOST;
      }

      ImageIcon icon = IconLoader.getDefaultIcon();
      boolean hostToBeInput = true;
      String newHost = null;

      while(hostToBeInput) {
         newHost = (String)JOptionPane.showInputDialog(this.component, "Enter new CoMaS location or press Cancel to exit", "CoMaS Server Input", -1, icon, (Object[])null, oldHost);
         if (newHost != null) {
            newHost = newHost.trim();
            String[] tokens = newHost.split(":");

            try {
               if (tokens != null && tokens.length > 1) {
                  String hostToTest = tokens[0];
                  InetAddress.getByName(tokens[0]);
               }

               hostToBeInput = false;
            } catch (UnknownHostException var8) {
               JOptionPane.showMessageDialog((Component)null, String.format("CoMaS server %s does not exist.\nPlease re-enter", newHost), "CoMaS Server Input Alert", 0, IconLoader.getIcon(0));
               hostToBeInput = true;
            }
         } else {
            hostToBeInput = false;
         }
      }

      return newHost;
   }

   private void askToDeleteConfiguration() {
      if (this.config.hasHost()) {
         int choice = JOptionPane.showConfirmDialog(this.component, "Delete current CoMaS configuration?", "CoMaS Default", 0, 2, IconLoader.getIcon(2));
         if (choice == 0) {
            Logger.output("Deleting " + ClientShared.COMAS_DOT_INI);
            this.config.delete();
         }
      }

   }

   public void forgetHosts() {
      this.config.forgetHosts();
   }

   public void removeHost(String server) {
      this.config.removeHost(server);
   }

   public void addHost(String server) {
      this.config.addHost(server);
   }

   public String getFirst() {
      return this.config.getFirst();
   }

   public String getLast() {
      return this.config.getLast();
   }

   public String getID() {
      return this.config.getID();
   }
}
