package micdm.transportlive2.ui.views;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.javatuples.Pair;
import org.joda.time.Duration;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import butterknife.BindView;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import micdm.transportlive2.ComponentHolder;
import micdm.transportlive2.R;
import micdm.transportlive2.data.loaders.Result;
import micdm.transportlive2.misc.CommonFunctions;
import micdm.transportlive2.misc.Id;
import micdm.transportlive2.misc.Irrelevant;
import micdm.transportlive2.misc.ObservableCache;
import micdm.transportlive2.models.ImmutablePoint;
import micdm.transportlive2.models.ImmutablePreferences;
import micdm.transportlive2.models.Path;
import micdm.transportlive2.models.Point;
import micdm.transportlive2.models.Route;
import micdm.transportlive2.models.RouteGroup;
import micdm.transportlive2.models.Station;
import micdm.transportlive2.models.Vehicle;
import micdm.transportlive2.ui.presenters.Presenters;
import micdm.transportlive2.ui.misc.ActivityLifecycleWatcher;
import micdm.transportlive2.ui.misc.ActivityLifecycleWatcher.Stage;
import micdm.transportlive2.ui.misc.ColorConstructor;
import micdm.transportlive2.ui.misc.PermissionChecker;
import micdm.transportlive2.ui.misc.VehicleMarkerIconBuilder;

public class CustomMapView extends PresentedView {

    private static class VehicleHandler {

        private static class VehicleMarker {

            Vehicle vehicle;
            final Marker marker;
            VehicleMarkerIconBuilder.BitmapWrapper bitmapWrapper;
            final ValueAnimator animator;

            private VehicleMarker(Vehicle vehicle, Marker marker, ValueAnimator animator) {
                this.vehicle = vehicle;
                this.marker = marker;
                this.animator = animator;
                animator.addUpdateListener(animation -> marker.setPosition((LatLng) animation.getAnimatedValue()));
            }

            void cleanup() {
                marker.remove();
                if (bitmapWrapper != null) {
                    bitmapWrapper.recycle();
                }
                animator.removeAllUpdateListeners();
                animator.cancel();
            }
        }

        private final Provider<ValueAnimator> vehicleMarkerAnimatorProvider;
        private final VehicleMarkerIconBuilder vehicleMarkerIconBuilder;
        private final GoogleMap map;
        private final Map<Id, VehicleMarker> markers = new HashMap<>();

        VehicleHandler(Provider<ValueAnimator> vehicleMarkerAnimatorProvider, VehicleMarkerIconBuilder vehicleMarkerIconBuilder, GoogleMap map) {
            this.vehicleMarkerAnimatorProvider = vehicleMarkerAnimatorProvider;
            this.vehicleMarkerIconBuilder = vehicleMarkerIconBuilder;
            this.map = map;
        }

        void handle(Collection<RouteGroup> groups, Collection<Vehicle> vehicles) {
            Collection<Id> outdated = new HashSet<>(markers.keySet());
            for (Vehicle vehicle: vehicles) {
                VehicleMarker vehicleMarker = markers.get(vehicle.id());
                if (vehicleMarker == null) {
                    vehicleMarker = newMarker(vehicle);
                    markers.put(vehicle.id(), vehicleMarker);
                } else {
                    outdated.remove(vehicle.id());
                    if (vehicleMarker.vehicle.equals(vehicle)) {
                        continue;
                    }
                    vehicleMarker.vehicle = vehicle;
                    updatePosition(vehicleMarker.marker, vehicle.position(), vehicleMarker.animator);
                }
                updateIcon(vehicleMarker, groups, vehicle);
            }
            for (Id id: outdated) {
                markers.remove(id).cleanup();
            }
        }

        private VehicleMarker newMarker(Vehicle vehicle) {
            MarkerOptions options = new MarkerOptions()
                .anchor(0.5f, 0.5f)
                .flat(true)
                .position(new LatLng(vehicle.position().latitude(), vehicle.position().longitude()))
                .zIndex(1);
            return new VehicleMarker(vehicle, map.addMarker(options), vehicleMarkerAnimatorProvider.get());
        }

        private void updatePosition(Marker marker, Point position, ValueAnimator animator) {
            animator.cancel();
            animator.setObjectValues(marker.getPosition(), new LatLng(position.latitude(), position.longitude()));
            animator.start();
        }

        private void updateIcon(VehicleMarker vehicleMarker, Collection<RouteGroup> groups, Vehicle vehicle) {
            if (vehicleMarker.bitmapWrapper != null) {
                vehicleMarker.bitmapWrapper.recycle();
                vehicleMarker.bitmapWrapper = null;
            }
            Route route = getRouteById(groups, vehicle.routeId());
            vehicleMarker.bitmapWrapper = vehicleMarkerIconBuilder.build(route.id().getOriginal(), route.number(), vehicle.direction());
            vehicleMarker.marker.setIcon(BitmapDescriptorFactory.fromBitmap(vehicleMarker.bitmapWrapper.getBitmap()));
        }

        private Route getRouteById(Collection<RouteGroup> groups, Id routeId) {
            for (RouteGroup group: groups) {
                for (Route route: group.routes()) {
                    if (route.id().equals(routeId)) {
                        return route;
                    }
                }
            }
            throw new IllegalStateException(String.format("cannot find routeId %s", routeId));
        }
    }

    private static class PathHandler {

        private final ColorConstructor colorConstructor;
        private final GoogleMap map;
        private final Map<Id, Polyline> polylines = new HashMap<>();

        PathHandler(ColorConstructor colorConstructor, GoogleMap map) {
            this.colorConstructor = colorConstructor;
            this.map = map;
        }

        void handle(Collection<Path> paths) {
            Collection<Id> outdated = new HashSet<>(polylines.keySet());
            for (Path path: paths) {
                if (!polylines.containsKey(path.routeId())) {
                    polylines.put(path.routeId(), newPolyline(path));
                } else {
                    outdated.remove(path.routeId());
                }
            }
            for (Id id: outdated) {
                polylines.remove(id).remove();
            }
        }

        private Polyline newPolyline(Path path) {
            PolylineOptions options = new PolylineOptions()
                .color(colorConstructor.getByString(path.routeId().getOriginal()) & 0x55FFFFFF)
                .width(4);
            for (Point point : path.points()) {
                options.add(new LatLng(point.latitude(), point.longitude()));
            }
            return map.addPolyline(options);
        }
    }

    private static class StationHandler {

        private final Bitmap icon;
        private final GoogleMap map;
        private final Map<Id, Marker> markers = new HashMap<>();

        StationHandler(Bitmap icon, GoogleMap map) {
            this.icon = icon;
            this.map = map;
        }

        void handle(Collection<Path> paths, boolean needShowStations) {
            Collection<Id> outdated = new HashSet<>(markers.keySet());
            for (Path path: paths) {
                for (Station station: path.stations()) {
                    Marker marker = markers.get(station.id());
                    if (marker == null) {
                        marker = newMarker(station, needShowStations);
                        marker.setTag(station.id());
                        markers.put(station.id(), marker);
                    } else {
                        outdated.remove(station.id());
                        marker.setVisible(needShowStations);
                    }
                }
            }
            for (Id id: outdated) {
                markers.remove(id).remove();
            }
        }

        private Marker newMarker(Station station, boolean needShow) {
            return map.addMarker(
                new MarkerOptions()
                    .anchor(0.5f, 0.5f)
                    .flat(true)
                    .icon(BitmapDescriptorFactory.fromBitmap(icon))
                    .position(new LatLng(station.location().latitude(), station.location().longitude()))
                    .title(station.name())
                    .visible(needShow)
            );
        }
    }

    private static final Duration LOAD_VEHICLES_INTERVAL = Duration.standardSeconds(10);
    private static final int MAX_ROUTE_COUNT_WITH_NO_PENALTY = 3;
    private static final Duration LOAD_VEHICLES_PENALTY_INTERVAL = Duration.standardSeconds(5);

    private static final int CAMERA_ZOOM_TO_SHOW_STATIONS = 12;

    @Inject
    ActivityLifecycleWatcher activityLifecycleWatcher;
    @Inject
    @Named("stationIcon")
    Bitmap stationIcon;
    @Inject
    ColorConstructor colorConstructor;
    @Inject
    CommonFunctions commonFunctions;
    @Inject
    ObservableCache observableCache;
    @Inject
    PermissionChecker permissionChecker;
    @Inject
    Presenters presenters;
    @Inject
    Resources resources;
    @Inject
    Provider<ValueAnimator> vehicleMarkerAnimatorProvider;
    @Inject
    VehicleMarkerIconBuilder vehicleMarkerIconBuilder;

    @BindView(R.id.v__custom_map__no_vehicles)
    View noVehiclesView;
    @BindView(R.id.v__custom_map__map)
    MapView mapView;

    public CustomMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (!isInEditMode()) {
            ComponentHolder.getActivityComponent().inject(this);
        }
    }

    @Override
    void inflateContent(LayoutInflater layoutInflater) {
        layoutInflater.inflate(R.layout.v__custom_map, this);
    }

    @Override
    void setupViews() {
        noVehiclesView.setVisibility(GONE);
    }

    @Override
    Disposable subscribeForEvents() {
        return new CompositeDisposable(
            subscribeForLoadRoutesRequests(),
            subscribeForLoadPathsRequests(),
            subscribeForLoadVehiclesRequests(),
            subscribeForChangePreferencesRequests(),
            subscribeForSetCurrentStationRequests(),
            subscribeForActivityEvents(),
            subscribeForMap(),
            subscribeForVehicles(),
            subscribeForPaths()
        );
    }

    private Disposable subscribeForLoadRoutesRequests() {
        return Observable.just(Irrelevant.INSTANCE)
            .subscribe(o -> presenters.getRoutesPresenter().viewInput.loadRoutes());
    }

    private Disposable subscribeForLoadPathsRequests() {
        return presenters.getPreferencesPresenter().getSelectedRoutes()
            .subscribe(presenters.getPathsPresenter().viewInput::loadPaths);
    }

    private Disposable subscribeForLoadVehiclesRequests() {
        return activityLifecycleWatcher.getState(Stage.RESUME, true)
            .switchMap(o -> presenters.getPreferencesPresenter().getSelectedRoutes())
            .switchMap(routeIds -> {
                Duration interval;
                if (routeIds.size() > MAX_ROUTE_COUNT_WITH_NO_PENALTY) {
                    interval = LOAD_VEHICLES_PENALTY_INTERVAL.multipliedBy(routeIds.size());
                } else {
                    interval = LOAD_VEHICLES_INTERVAL;
                }
                return Observable
                    .interval(0, interval.getStandardSeconds(), TimeUnit.SECONDS)
                    .compose(commonFunctions.toConst(routeIds))
                    .takeUntil(activityLifecycleWatcher.getState(Stage.PAUSE, true));
            })
            .subscribe(presenters.getAllVehiclesPresenter().viewInput::loadVehicles);
    }

    private Disposable subscribeForChangePreferencesRequests() {
        return getCameraPosition()
            .withLatestFrom(presenters.getPreferencesPresenter().getPreferences(), (cameraPosition, preferences) ->
                ImmutablePreferences.builder()
                    .from(preferences)
                    .cameraPosition(
                        ImmutablePreferences.CameraPosition.builder()
                            .position(
                                ImmutablePoint.builder()
                                    .latitude(cameraPosition.target.latitude)
                                    .longitude(cameraPosition.target.longitude)
                                    .build()
                            )
                            .zoom(cameraPosition.zoom)
                            .build()
                    )
                    .build()
            )
            .subscribe(presenters.getPreferencesPresenter().viewInput::changePreferences);
    }

    private Disposable subscribeForSetCurrentStationRequests() {
        return getMap()
            .switchMap(map ->
                Observable.<Id>create(source -> {
                    map.setOnMarkerClickListener(marker -> {
                        Object tag = marker.getTag();
                        if (tag != null && tag instanceof Id) {
                            source.onNext((Id) tag);
                            return true;
                        }
                        return false;
                    });
                    source.setCancellable(() -> map.setOnMarkerClickListener(null));
                })
            )
            .subscribe(presenters.getCurrentStationPresenter().viewInput::setCurrentStation);
    }

    private Disposable subscribeForActivityEvents() {
        return new CompositeDisposable(
            activityLifecycleWatcher.getState(Stage.CREATE)
                .subscribe(state -> mapView.onCreate(state.extra)),
            activityLifecycleWatcher.getState(Stage.START)
                .subscribe(state -> mapView.onStart()),
            activityLifecycleWatcher.getState(Stage.RESUME)
                .subscribe(state -> mapView.onResume()),
            activityLifecycleWatcher.getState(Stage.PAUSE)
                .subscribe(state -> mapView.onPause()),
            activityLifecycleWatcher.getState(Stage.STOP)
                .subscribe(state -> mapView.onStop()),
            activityLifecycleWatcher.getState(Stage.DESTROY)
                .subscribe(state -> mapView.onDestroy()),
            activityLifecycleWatcher.getState(Stage.SAVE_STATE)
                .subscribe(state -> mapView.onSaveInstanceState(state.extra)),
            activityLifecycleWatcher.getState(Stage.LOW_MEMORY)
                .subscribe(state -> mapView.onLowMemory())
        );
    }

    private Disposable subscribeForMap() {
        return new CompositeDisposable(
            getMap().subscribe(map -> {
                UiSettings uiSettings = map.getUiSettings();
                uiSettings.setMapToolbarEnabled(false);
                uiSettings.setZoomControlsEnabled(true);
                map.setPadding(0, resources.getDimensionPixelSize(R.dimen.map_top_padding), 0, 0);
            }),
            getMap()
                .withLatestFrom(presenters.getPreferencesPresenter().getCameraPosition(), commonFunctions::wrap)
                .subscribe(commonFunctions.unwrap((map, camera) -> {
                    CameraPosition position = CameraPosition.fromLatLngZoom(new LatLng(camera.position().latitude(),
                                                                                       camera.position().longitude()),
                                                                                       (float) camera.zoom());
                    map.moveCamera(CameraUpdateFactory.newCameraPosition(position));
                })),
            Observable
                .combineLatest(
                    getMap(),
                    permissionChecker.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION),
                    commonFunctions::wrap
                )
                .subscribe(commonFunctions.unwrap(GoogleMap::setMyLocationEnabled))
        );
    }

    private Disposable subscribeForVehicles() {
        return new CompositeDisposable(
            getMap()
                .switchMap(map ->
                    Observable.combineLatest(
                        presenters.getRoutesPresenter().getResults()
                            .filter(Result::isSuccess)
                            .map(Result::getData)
                            .distinctUntilChanged(),
                        Observable.merge(
                            presenters.getAllVehiclesPresenter().getResults()
                                .filter(Result::isSuccess)
                                .map(Result::getData),
                            presenters.getPreferencesPresenter().getSelectedRoutes()
                                .filter(Collection::isEmpty)
                                .map(o -> Collections.<Vehicle>emptyList())
                        ).distinctUntilChanged(),
                        Pair::with
                    )
                    .compose(commonFunctions.toMainThread())
                    .scan(new VehicleHandler(vehicleMarkerAnimatorProvider, vehicleMarkerIconBuilder, map), (accumulated, pair) -> {
                        accumulated.handle(pair.getValue0(), pair.getValue1());
                        return accumulated;
                    })
                )
                .subscribe(),
            Observable
                .combineLatest(
                    presenters.getAllVehiclesPresenter().getResults()
                        .filter(Result::isSuccess)
                        .map(Result::getData),
                    presenters.getPreferencesPresenter().getSelectedRoutes(),
                    (vehicles, routeIds) -> vehicles.isEmpty() && !routeIds.isEmpty()
                )
                .compose(commonFunctions.toMainThread())
                .subscribe(isEmpty -> noVehiclesView.setVisibility(isEmpty ? VISIBLE : GONE))
        );
    }

    private Disposable subscribeForPaths() {
        Observable<Collection<Path>> common =
            Observable
                .merge(
                    presenters.getPathsPresenter().getResults()
                        .filter(Result::isSuccess)
                        .map(Result::getData),
                    presenters.getPreferencesPresenter().getSelectedRoutes()
                        .filter(Collection::isEmpty)
                        .map(o -> Collections.<Path>emptyList())
                );
        return getMap()
            .switchMap(map ->
                Observable.merge(
                    common
                        .compose(commonFunctions.toMainThread())
                        .scan(new PathHandler(colorConstructor, map), (accumulated, paths) -> {
                            accumulated.handle(paths);
                            return accumulated;
                        }),
                    Observable
                        .combineLatest(
                            common,
                            Observable.combineLatest(
                                presenters.getPreferencesPresenter().getNeedShowStations(),
                                getCameraPosition().map(cameraPosition -> cameraPosition.zoom >= CAMERA_ZOOM_TO_SHOW_STATIONS),
                                (a, b) -> a && b
                            ),
                            Pair::with
                        )
                        .compose(commonFunctions.toMainThread())
                        .scan(new StationHandler(stationIcon, map), (accumulated, pair) -> {
                            accumulated.handle(pair.getValue0(), pair.getValue1());
                            return accumulated;
                        })
                )
            )
            .subscribe();
    }

    private Observable<GoogleMap> getMap() {
        return observableCache.get("getMap", () ->
            Observable.<GoogleMap>create(source -> mapView.getMapAsync(source::onNext))
                .replay()
                .refCount()
        );
    }

    private Observable<CameraPosition> getCameraPosition() {
        return observableCache.get("getCameraPosition", () ->
            getMap()
                .switchMap(map ->
                    Observable.<CameraPosition>create(source -> {
                        map.setOnCameraIdleListener(() -> source.onNext(map.getCameraPosition()));
                        source.setCancellable(() -> map.setOnCameraIdleListener(null));
                    })
                )
                .replay()
                .refCount()
        );
    }
}
