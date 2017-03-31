package micdm.transportlive.data;

import java.util.ArrayList;
import java.util.Collection;

import javax.inject.Inject;

import io.reactivex.Observable;
import micdm.transportlive.misc.CommonFunctions;
import micdm.transportlive.misc.Irrelevant;
import micdm.transportlive.models.ImmutablePoint;
import micdm.transportlive.models.ImmutableVehicle;
import micdm.transportlive.models.Vehicle;

public class VehiclesLoader extends DefaultLoader<VehiclesLoader.Client, Collection<Vehicle>> {

    public interface Client {

        Observable<String> getLoadVehiclesRequests();
        Observable<String> getReloadVehiclesRequests();
        Observable<String> getCancelVehiclesLoadingRequests();
    }

    @Inject
    CommonFunctions commonFunctions;
    @Inject
    PathsStore pathsStore;
    @Inject
    ServerConnector serverConnector;

    private final String routeId;

    VehiclesLoader(String routeId) {
        this.routeId = routeId;
    }

    @Override
    protected Observable<Object> getLoadRequests() {
        return clients.get()
            .flatMap(Client::getLoadVehiclesRequests)
            .filter(commonFunctions.isEqual(routeId))
            .compose(commonFunctions.toConst(Irrelevant.INSTANCE));
    }

    @Override
    Observable<Object> getReloadRequests() {
        return clients.get()
            .flatMap(Client::getReloadVehiclesRequests)
            .filter(commonFunctions.isEqual(routeId))
            .compose(commonFunctions.toConst(Irrelevant.INSTANCE));
    }

    @Override
    protected Observable<Object> getCancelRequests() {
        return clients.get()
            .flatMap(Client::getCancelVehiclesLoadingRequests)
            .filter(commonFunctions.isEqual(routeId))
            .compose(commonFunctions.toConst(Irrelevant.INSTANCE));
    }

    @Override
    protected Observable<Collection<Vehicle>> loadFromCache() {
        return Observable.empty();
    }

    @Override
    protected Observable<Collection<Vehicle>> loadFromServer() {
        return serverConnector.getVehicles(routeId)
            .toObservable()
            .map(response -> {
                Collection<Vehicle> result = new ArrayList<>();
                for (GetVehiclesResponse item: response) {
                    result.add(
                        ImmutableVehicle.builder()
                            .id(item.Auto.AutoId)
                            .route(item.Auto.PathwayId)
                            .position(
                                ImmutablePoint.builder()
                                    .latitude(item.Point.Lat)
                                    .longitude(item.Point.Lon)
                                    .build()
                            )
                            .direction(item.Point.Dir)
                            .build()
                    );
                }
                return result;
            });
    }
}
