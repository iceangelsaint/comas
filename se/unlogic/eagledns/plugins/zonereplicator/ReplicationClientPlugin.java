package se.unlogic.eagledns.plugins.zonereplicator;

import java.lang.reflect.Field;
import java.rmi.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.UnknownHostException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Timer;
import javax.sql.DataSource;
import se.unlogic.eagledns.Status;
import se.unlogic.eagledns.plugins.BasePlugin;
import se.unlogic.eagledns.zoneproviders.db.beans.DBZone;
import se.unlogic.standardutils.dao.AnnotatedDAO;
import se.unlogic.standardutils.dao.HighLevelQuery;
import se.unlogic.standardutils.dao.RelationQuery;
import se.unlogic.standardutils.dao.SimpleAnnotatedDAOFactory;
import se.unlogic.standardutils.dao.SimpleDataSource;
import se.unlogic.standardutils.dao.TransactionHandler;
import se.unlogic.standardutils.numbers.NumberUtils;
import se.unlogic.standardutils.rmi.PasswordLogin;
import se.unlogic.standardutils.timer.RunnableTimerTask;

public class ReplicationClientPlugin extends BasePlugin implements Runnable {
   private static final HighLevelQuery ALL_ZONES_QUERY = new HighLevelQuery();
   private static final RelationQuery RELATION_QUERY;
   private String serverAddress;
   private String rmiPassword;
   private Integer rmiPort;
   private String driver;
   private String url;
   private String username;
   private String password;
   private AnnotatedDAO zoneDAO;
   private Timer timer;
   private int replicationInterval = 60;

   static {
      ALL_ZONES_QUERY.disableAutoRelations(true);
      RELATION_QUERY = new RelationQuery(new Field[]{DBZone.RECORDS_RELATION});
   }

   public void init(String name) throws Exception {
      super.init(name);

      DataSource dataSource;
      try {
         dataSource = new SimpleDataSource(this.driver, this.url, this.username, this.password);
      } catch (ClassNotFoundException e) {
         this.log.error("Unable to load JDBC driver " + this.driver, e);
         throw e;
      }

      SimpleAnnotatedDAOFactory annotatedDAOFactory = new SimpleAnnotatedDAOFactory();
      this.zoneDAO = new AnnotatedDAO(dataSource, DBZone.class, annotatedDAOFactory);
      this.timer = new Timer(name, true);
      this.timer.scheduleAtFixedRate(new RunnableTimerTask(this), 0L, (long)(this.replicationInterval * 1000));
      this.log.info("Plugin " + this.name + " started with replication interval of " + this.replicationInterval + " seconds.");
   }

   public void shutdown() throws Exception {
      if (this.timer != null) {
         this.timer.cancel();
      }

      super.shutdown();
   }

   public void run() {
      if (this.systemInterface.getStatus() != Status.STARTED) {
         this.log.debug("Incorrect system status skipping replication");
      }

      this.log.debug("Replication starting...");
      TransactionHandler transactionHandler = null;

      try {
         transactionHandler = this.zoneDAO.createTransaction();
         List<DBZone> zones = this.zoneDAO.getAll(ALL_ZONES_QUERY, transactionHandler);
         ReplicationServer server = this.getServer();
         ReplicationResponse response = server.replicate(zones);
         if (response != null) {
            this.log.info("Replication got " + response + " from server " + this.serverAddress + ":" + this.rmiPort + ", persisting changes...");
            if (response.getNewZones() != null) {
               this.zoneDAO.addAll(response.getNewZones(), transactionHandler, RELATION_QUERY);
            }

            if (response.getUpdatedZones() != null) {
               this.zoneDAO.update(response.getUpdatedZones(), transactionHandler, RELATION_QUERY);
            }

            if (response.getDeletedZones() != null) {
               this.zoneDAO.delete(response.getDeletedZones(), transactionHandler);
            }

            transactionHandler.commit();
            this.log.info("Replication completed succesfully, reloading zones.");
            this.systemInterface.reloadZones();
            return;
         }

         this.log.debug("Replication completed succesfully, no changes found on server.");
      } catch (ConnectException e) {
         this.log.warn("Error connecting to server, " + e);
         return;
      } catch (UnknownHostException e) {
         this.log.warn("Error connecting to server, " + e);
         return;
      } catch (Exception e) {
         this.log.error("Error replicating zones from server", e);
         return;
      } finally {
         TransactionHandler.autoClose(transactionHandler);
      }

   }

   private ReplicationServer getServer() throws RemoteException, NotBoundException {
      Registry registry = LocateRegistry.getRegistry(this.serverAddress, this.rmiPort);
      PasswordLogin<ReplicationServerPlugin> loginHandler = (PasswordLogin)registry.lookup("replicationLoginHandler");
      return (ReplicationServer)loginHandler.login(this.rmiPassword);
   }

   public void setServerAddress(String serverAddress) {
      this.serverAddress = serverAddress;
   }

   public void setRmiPassword(String rmiPassword) {
      this.rmiPassword = rmiPassword;
   }

   public void setRmiPort(String rmiPort) {
      this.rmiPort = NumberUtils.toInt(rmiPort);
   }

   public void setDriver(String driver) {
      this.driver = driver;
   }

   public void setUrl(String url) {
      this.url = url;
   }

   public void setUsername(String username) {
      this.username = username;
   }

   public void setPassword(String password) {
      this.password = password;
   }

   public void setReplicationInterval(String replicationInterval) {
      this.replicationInterval = NumberUtils.toInt(replicationInterval);
   }
}
