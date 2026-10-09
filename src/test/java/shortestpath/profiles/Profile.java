package shortestpath.profiles;

/** A named starting point for a scenario's account and plugin settings. */
public interface Profile {
    String name();

    /** A fresh account builder and settings object for one scenario. */
    Setup setup(ProfileContext context);
}
