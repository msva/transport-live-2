package micdm.transportlive.models;

import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
public interface RouteGroup {

    String id();
    String name();
    List<Route> routes();
}
