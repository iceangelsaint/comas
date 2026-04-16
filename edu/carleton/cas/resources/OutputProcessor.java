package edu.carleton.cas.resources;

public interface OutputProcessor {
   void process(String var1);

   void asyncResult();

   String result();
}
