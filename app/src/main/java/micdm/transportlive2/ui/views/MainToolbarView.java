package micdm.transportlive2.ui.views;

import android.content.Context;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MenuItem;

import com.jakewharton.rxbinding2.support.v7.widget.RxToolbar;

import javax.inject.Inject;

import butterknife.BindView;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import micdm.transportlive2.ComponentHolder;
import micdm.transportlive2.R;
import micdm.transportlive2.misc.CommonFunctions;
import micdm.transportlive2.misc.ObservableCache;
import micdm.transportlive2.models.ImmutablePreferences;
import micdm.transportlive2.models.Preferences;
import micdm.transportlive2.ui.PreferencesPresenter;
import micdm.transportlive2.ui.Presenters;

public class MainToolbarView extends PresentedView implements PreferencesPresenter.View {

    @Inject
    CommonFunctions commonFunctions;
    @Inject
    ObservableCache observableCache;
    @Inject
    Presenters presenters;

    @BindView(R.id.v__main_toolbar__toolbar)
    Toolbar toolbarView;

    public MainToolbarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (!isInEditMode()) {
            ComponentHolder.getActivityComponent().inject(this);
        }
    }

    @Override
    void inflateContent(LayoutInflater layoutInflater) {
        layoutInflater.inflate(R.layout.v__main_toolbar, this);
    }

    @Override
    void setupViews() {
        toolbarView.inflateMenu(R.menu.main);
    }

    @Override
    Disposable subscribeForEvents() {
        return subscribeForShowStations();
    }

    private Disposable subscribeForShowStations() {
        return presenters.getPreferencesPresenter().getNeedShowStations()
            .subscribe(toolbarView.getMenu().findItem(R.id.m__main__show_stations)::setChecked);
    }

    @Override
    void attachToPresenters() {
        presenters.getPreferencesPresenter().attach(this);
    }

    @Override
    void detachFromPresenters() {
        presenters.getPreferencesPresenter().detach(this);
    }

    Observable<Object> getGoToAboutRequests() {
        return getMenuClicks()
            .filter(menuItem -> menuItem.getItemId() == R.id.m__main__about)
            .compose(commonFunctions.toNothing());
    }

    private Observable<MenuItem> getMenuClicks() {
        return observableCache.get("getMenuClicks", () -> RxToolbar.itemClicks(toolbarView).share());
    }

    @Override
    public Observable<Preferences> getChangePreferencesRequests() {
        return getMenuClicks()
            .filter(menuItem -> menuItem.getItemId() == R.id.m__main__show_stations)
            .map(item -> !item.isChecked())
            .withLatestFrom(presenters.getPreferencesPresenter().getPreferences(), (needShowStations, preferences) ->
                ImmutablePreferences.builder()
                    .from(preferences)
                    .needShowStations(needShowStations)
                    .build()
            );
    }
}
