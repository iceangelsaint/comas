package se.unlogic.eagledns.plugins.zonereplicator;

import java.lang.reflect.Field;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ServerNotActiveException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import se.unlogic.eagledns.plugins.BasePlugin;
import se.unlogic.eagledns.zoneproviders.db.beans.DBZone;
import se.unlogic.standardutils.collections.CollectionUtils;
import se.unlogic.standardutils.dao.AnnotatedDAO;
import se.unlogic.standardutils.dao.HighLevelQuery;
import se.unlogic.standardutils.dao.QueryOperators;
import se.unlogic.standardutils.dao.QueryParameterFactory;
import se.unlogic.standardutils.dao.SimpleAnnotatedDAOFactory;
import se.unlogic.standardutils.dao.SimpleDataSource;
import se.unlogic.standardutils.dao.TransactionHandler;
import se.unlogic.standardutils.numbers.NumberUtils;
import se.unlogic.standardutils.rmi.PasswordLogin;

public class ReplicationServerPlugin extends BasePlugin implements Remote, ReplicationServer {
   private String rmiPassword;
   private Integer rmiPort;
   private String driver;
   private String url;
   private String username;
   private String password;
   private AnnotatedDAO zoneDAO;
   private HighLevelQuery allZonesQuery;
   private QueryParameterFactory zoneIDParamFactory;
   private QueryParameterFactory serialParamFactory;
   private QueryParameterFactory enabledParamFactory;
   private ReplicationLoginHandler replicationLoginHandler;

   public ReplicationServerPlugin() {
      this.allZonesQuery = new HighLevelQuery(new Field[]{DBZone.RECORDS_RELATION});
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
      this.zoneIDParamFactory = this.zoneDAO.getParamFactory("zoneID", Integer.class);
      this.serialParamFactory = this.zoneDAO.getParamFactory("serial", Long.class);
      this.enabledParamFactory = this.zoneDAO.getParamFactory("enabled", Boolean.TYPE);
      super.init(name);
      if (this.rmiPassword != null && this.rmiPort != null) {
         this.replicationLoginHandler = new ReplicationLoginHandler(this, this.rmiPassword);
         PasswordLogin<ReplicationServerPlugin> loginHandler = (PasswordLogin)UnicastRemoteObject.exportObject(this.replicationLoginHandler, this.rmiPort);
         UnicastRemoteObject.exportObject(this, this.rmiPort);
         Registry registry = LocateRegistry.createRegistry(this.rmiPort);
         registry.bind("replicationLoginHandler", loginHandler);
         this.log.info("Plugin " + this.name + " started with RMI interface on port " + this.rmiPort);
      } else {
         throw new RuntimeException("RMI port and/or password not set");
      }
   }

   public ReplicationResponse replicate(List clientZones) throws ReplicationException, RemoteException, ServerNotActiveException {
      String clientURL = UnicastRemoteObject.getClientHost();
      this.log.debug("Starting replication for client connecting from " + clientURL + " with " + CollectionUtils.getSize(clientZones) + " zones.");
      TransactionHandler transactionHandler = null;

      try {
         transactionHandler = this.zoneDAO.createTransaction();
         if (clientZones != null) {
            List<DBZone> newZones = this.getNewZones(clientZones, transactionHandler);
            List<DBZone> updatedZones = this.getUpdatedZones(clientZones, transactionHandler);
            List<DBZone> deletedZones = this.getDeletedZones(clientZones, transactionHandler);
            transactionHandler.commit();
            if (newZones == null && updatedZones == null && deletedZones == null) {
               this.log.debug("Replication finished, no changes found");
               return null;
            }

            ReplicationResponse response = new ReplicationResponse(newZones, updatedZones, deletedZones);
            this.log.info("Replication changes found for client connecting from " + clientURL + " sending " + response);
            ReplicationResponse var18 = response;
            return var18;
         }

         List<DBZone> dbZones = this.zoneDAO.getAll(this.allZonesQuery, transactionHandler);
         if (dbZones != null) {
            ReplicationResponse response = new ReplicationResponse(dbZones, (List)null, (List)null);
            this.log.info("Replication changes found for client connecting from " + clientURL + " sending " + response);
            ReplicationResponse var9 = response;
            return var9;
         }
      } catch (SQLException e) {
         this.log.error("Error during replication", e);
         throw new ReplicationException();
      } catch (RuntimeException e) {
         this.log.error("Error during replication", e);
         throw new ReplicationException();
      } finally {
         TransactionHandler.autoClose(transactionHandler);
      }

      return null;
   }

   private List getNewZones(List clientZones, TransactionHandler transactionHandler) throws SQLException {
      HighLevelQuery<DBZone> query = new HighLevelQuery(new Field[]{DBZone.RECORDS_RELATION});
      List<Integer> zoneIDList = new ArrayList(clientZones.size());

      for(DBZone dbZone : clientZones) {
         zoneIDList.add(dbZone.getZoneID());
      }

      query.addParameter(this.zoneIDParamFactory.getWhereNotInParameter(zoneIDList));
      return this.zoneDAO.getAll(query, transactionHandler);
   }

   private List getUpdatedZones(List clientZones, TransactionHandler transactionHandler) throws SQLException {
      List<DBZone> updatedZones = new ArrayList(clientZones.size());

      for(DBZone dbZone : clientZones) {
         HighLevelQuery<DBZone> query = new HighLevelQuery(new Field[]{DBZone.RECORDS_RELATION});
         query.addParameter(this.zoneIDParamFactory.getParameter(dbZone.getZoneID()));
         if (dbZone.getSerial() != null) {
            query.addParameter(this.serialParamFactory.getParameter(dbZone.getSerial(), QueryOperators.NOT_EQUALS));
         } else {
            query.addParameter(this.serialParamFactory.getIsNotNullParameter());
         }

         DBZone updatedZone = (DBZone)this.zoneDAO.get(query, transactionHandler);
         if (updatedZone != null) {
            updatedZones.add(updatedZone);
         } else {
            query = new HighLevelQuery(new Field[]{DBZone.RECORDS_RELATION});
            query.addParameter(this.zoneIDParamFactory.getParameter(dbZone.getZoneID()));
            query.addParameter(this.enabledParamFactory.getParameter(dbZone.isEnabled(), QueryOperators.NOT_EQUALS));
            updatedZone = (DBZone)this.zoneDAO.get(query, transactionHandler);
            if (updatedZone != null) {
               updatedZones.add(updatedZone);
            }
         }
      }

      if (updatedZones.isEmpty()) {
         return null;
      } else {
         clientZones.removeAll(updatedZones);
         return updatedZones;
      }
   }

   private List getDeletedZones(List clientZones, TransactionHandler transactionHandler) throws SQLException {
      List<DBZone> deletedZones = new ArrayList(clientZones.size());

      for(DBZone dbZone : clientZones) {
         if (!this.zoneDAO.beanExists(dbZone, transactionHandler)) {
            deletedZones.add(dbZone);
         }
      }

      if (deletedZones.isEmpty()) {
         return null;
      } else {
         return deletedZones;
      }
   }

   public void setRmiServerHostname(String serverHost) {
      System.getProperties().put("java.rmi.server.hostname", serverHost);
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
}
