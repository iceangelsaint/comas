package edu.carleton.cas.jetty.embedded;

import edu.carleton.cas.exam.Invigilator;
import edu.carleton.cas.exam.InvigilatorState;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ServletRouter {
   Invigilator invigilator;
   ConcurrentHashMap map;
   ConcurrentHashMap redirect;

   ServletRouter(Invigilator invigilator) {
      this.invigilator = invigilator;
      this.map = new ConcurrentHashMap();
      this.redirect = new ConcurrentHashMap();
   }

   public boolean canAccessOrRedirect(HttpServletRequest request, HttpServletResponse response) throws IOException {
      if (!this.isAccessibleInState(request, response)) {
         if (!this.redirect(request, response)) {
            response.sendError(404);
         }

         return false;
      } else {
         return true;
      }
   }

   public boolean redirect(HttpServletRequest request, HttpServletResponse response) throws IOException {
      String mapping = (String)this.redirect.get(this.invigilator.getInvigilatorState());
      String path = request.getRequestURI();
      if (mapping != null && path != null && !path.equals(mapping)) {
         response.sendRedirect(mapping);
         return true;
      } else {
         return false;
      }
   }

   public String getRedirect() {
      return (String)this.redirect.get(this.invigilator.getInvigilatorState());
   }

   public boolean isAccessibleInState(HttpServletRequest request, HttpServletResponse response) {
      String path = request.getRequestURI();
      if (path == null) {
         return false;
      } else {
         HashSet<InvigilatorState> state = (HashSet)this.map.get(path);
         return state == null ? false : state.contains(this.invigilator.getInvigilatorState());
      }
   }

   public void addRedirect(InvigilatorState state, String mapping) {
      this.redirect.put(state, mapping);
   }

   public String removeRedirect(InvigilatorState state) {
      return (String)this.redirect.remove(state);
   }

   public void addRule(String mapping, InvigilatorState state) {
      HashSet<InvigilatorState> _state = (HashSet)this.map.get(mapping);
      if (_state == null) {
         _state = new HashSet();
         this.map.put(mapping, _state);
      }

      _state.add(state);
   }

   public HashSet removeRule(String mapping, InvigilatorState state) {
      HashSet<InvigilatorState> _state = (HashSet)this.map.get(mapping);
      if (_state != null) {
         _state.remove(state);
         if (_state.isEmpty()) {
            this.map.remove(mapping);
         }
      }

      return _state;
   }

   public HashSet removeRule(String mapping) {
      return (HashSet)this.map.remove(mapping);
   }

   public void configure() {
      Properties properties = this.invigilator.getProperties();
      String baseVariable = "servlet.rule.";
      int i = 1;
      String value = properties.getProperty(baseVariable + i);
      this.map.clear();

      while(value != null) {
         String[] tokens = value.split(",");
         if (tokens.length == 2) {
            this.addRule(tokens[0].trim(), InvigilatorState.parse(tokens[1].trim()));
         }

         ++i;
         value = properties.getProperty(baseVariable + i);
      }

      baseVariable = "servlet.redirect.";
      i = 1;
      value = properties.getProperty(baseVariable + i);
      this.redirect.clear();

      while(value != null) {
         String[] tokens = value.split(",");
         if (tokens.length == 2) {
            this.addRedirect(InvigilatorState.parse(tokens[0].trim()), tokens[1].trim());
         }

         ++i;
         value = properties.getProperty(baseVariable + i);
      }

   }
}
