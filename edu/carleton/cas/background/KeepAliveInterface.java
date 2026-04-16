package edu.carleton.cas.background;

public interface KeepAliveInterface extends ControlInterface {
   boolean keepAlive();

   String getName();

   KeepAliveStatistics getStatistics();
}
