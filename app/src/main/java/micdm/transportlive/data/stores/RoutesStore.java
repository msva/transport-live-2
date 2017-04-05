package micdm.transportlive.data.stores;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import javax.inject.Inject;

import io.reactivex.Observable;
import micdm.transportlive.misc.Id;
import micdm.transportlive.models.RouteGroup;

public class RoutesStore extends DefaultStore<RoutesStore.Client, Collection<RouteGroup>> {

    public interface Client {

        Observable<Collection<RouteGroup>> getStoreRoutesRequests();
    }

    @Inject
    Gson gson;

    @Override
    Observable<Collection<RouteGroup>> getStoreRequests() {
        return clients.get().flatMap(Client::getStoreRoutesRequests);
    }

    @Override
    Id getEntityId(Collection<RouteGroup> routes) {
        return null;
    }

    @Override
    String getKey(Id entityId) {
        return "routes";
    }

    @Override
    String serialize(Collection<RouteGroup> routes) {
        return gson.toJson(new ArrayList<>(routes).toArray(), RouteGroup[].class);
    }

    @Override
    Collection<RouteGroup> deserialize(String data) {
        return Arrays.asList(gson.fromJson(data, RouteGroup[].class));
    }
}
