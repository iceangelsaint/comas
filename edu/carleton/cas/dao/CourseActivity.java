package edu.carleton.cas.dao;

public final class CourseActivity {
   public final String activity;
   public final String course;

   public CourseActivity(String course, String activity) {
      this.course = course;
      this.activity = activity;
   }

   public String toString() {
      return this.course + "/" + this.activity;
   }
}
