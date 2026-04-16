package edu.carleton.cas.dao;

public final class StudentSession {
   public final Session session;
   public final Student student;

   public StudentSession(Session session, Student student) {
      this.session = session;
      this.student = student;
   }

   public StudentSession(Session session, String first, String last, String id) {
      this(session, new Student(first, last, id));
   }

   public String toString() {
      return String.format("%s for %s", this.student.toString(), this.session.toString());
   }
}
