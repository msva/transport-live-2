package micdm.transportlive.data;

import java.util.Collection;

import io.reactivex.Single;
import retrofit2.http.GET;
import retrofit2.http.Query;

interface ApiService {

    @GET("Pathway/")
    Single<Collection<GetRoutesResponse>> getRoutes();

    @GET("Navigation/GetAutoOnPathway")
    Single<Collection<GetVehiclesResponse>> getVehicles(@Query("pathwayId") String routeId);

    @GET("PathwayGeoJson")
    Single<GetPathResponse> getPath(@Query("pathwayId") String routeId);
}
