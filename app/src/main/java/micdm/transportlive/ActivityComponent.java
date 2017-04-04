package micdm.transportlive;

import dagger.Subcomponent;
import micdm.transportlive.ui.VehiclesController;
import micdm.transportlive.ui.misc.MarkerIconBuilder;
import micdm.transportlive.ui.views.CannotLoadView;
import micdm.transportlive.ui.views.CustomMapView;
import micdm.transportlive.ui.views.LoadingView;
import micdm.transportlive.ui.views.RoutesView;

@ActivityScope
@Subcomponent(modules = {ActivityModule.class})
public interface ActivityComponent {

    void inject(MainActivity target);
    void inject(CannotLoadView target);
    void inject(CustomMapView target);
    void inject(LoadingView target);
    void inject(RoutesView target);
    void inject(MarkerIconBuilder target);
    void inject(VehiclesController target);

    @Subcomponent.Builder
    interface Builder {
        Builder activityModule(ActivityModule module);
        ActivityComponent build();
    }
}
