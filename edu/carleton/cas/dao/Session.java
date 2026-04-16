package edu.carleton.cas.dao;

import edu.carleton.cas.utility.ClientConfiguration;
import java.util.Date;

public final class Session {
   public final CourseActivity courseActivity;
   public final long start;
   public final String ipAddress;

   public Session(CourseActivity courseActivity, long start, String ipAddress) {
      this.courseActivity = courseActivity;
      this.start = start;
      this.ipAddress = ipAddress;
   }

   public Session(String course, String activity, long start, String ipAddress) {
      this(new CourseActivity(course, activity), start, ipAddress);
   }

   public Session(ClientConfiguration cc) {
      this(new CourseActivity(cc.getCourse(), cc.getActivity()), cc.getLastSession(), cc.getIPv4Address());
   }

   public String toString() {
      return String.format("%s started on %s from %s", this.courseActivity.toString(), (new Date(this.start)).toString(), this.ipAddress);
   }
}
