package micdm.transportlive.ui.views;

import android.content.Context;
import android.content.res.Resources;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.jakewharton.rxbinding2.view.RxView;
import com.jakewharton.rxbinding2.widget.RxTextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import micdm.transportlive.ComponentHolder;
import micdm.transportlive.R;
import micdm.transportlive.data.loaders.Result;
import micdm.transportlive.misc.CommonFunctions;
import micdm.transportlive.misc.Irrelevant;
import micdm.transportlive.models.Route;
import micdm.transportlive.models.RouteGroup;
import micdm.transportlive.ui.PresenterStore;
import micdm.transportlive.ui.RoutesPresenter;
import micdm.transportlive.ui.SelectedRoutesPresenter;
import micdm.transportlive.ui.misc.MiscFunctions;

public class SearchRouteView extends BaseView implements RoutesPresenter.View, SelectedRoutesPresenter.View {

    private static class RouteInfo {

        final RouteGroup group;
        final Route route;

        RouteInfo(RouteGroup group, Route route) {
            this.group = group;
            this.route = route;
        }
    }

    static class Adapter extends RecyclerView.Adapter<Adapter.ViewHolder> {

        static class ViewHolder extends RecyclerView.ViewHolder {

            @BindView(R.id.v__search_route__item__name)
            TextView nameView;

            ViewHolder(View itemView) {
                super(itemView);
                ButterKnife.bind(this, itemView);
            }
        }

        private final LayoutInflater layoutInflater;
        private final MiscFunctions miscFunctions;
        private final Resources resources;

        private final Subject<String> selectRouteRequests = PublishSubject.create();
        private List<RouteInfo> routes = Collections.emptyList();

        Adapter(LayoutInflater layoutInflater, MiscFunctions miscFunctions, Resources resources) {
            this.layoutInflater = layoutInflater;
            this.miscFunctions = miscFunctions;
            this.resources = resources;
        }

        Observable<String> getSelectRouteRequests() {
            return selectRouteRequests;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(layoutInflater.inflate(R.layout.v__search__route__item, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            RouteInfo info = routes.get(position);
            holder.itemView.setOnClickListener(o -> selectRouteRequests.onNext(info.route.id()));
            holder.nameView.setText(resources.getString(R.string.v__search_route__route, miscFunctions.getRouteGroupName(info.group),
                                                        info.route.number(), info.route.source(), info.route.destination()));
        }

        @Override
        public int getItemCount() {
            return routes.size();
        }

        void setRoutes(List<RouteInfo> routes) {
            this.routes = routes;
            notifyDataSetChanged();
        }
    }

    @Inject
    CommonFunctions commonFunctions;
    @Inject
    LayoutInflater layoutInflater;
    @Inject
    MiscFunctions miscFunctions;
    @Inject
    PresenterStore presenterStore;
    @Inject
    Resources resources;

    @BindView(R.id.v__search_route__input)
    TextView inputView;
    @BindView(R.id.v__search_route__items)
    RecyclerView itemsView;

    public SearchRouteView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (!isInEditMode()) {
            ComponentHolder.getActivityComponent().inject(this);
        }
    }

    @Override
    void inflateContent(LayoutInflater layoutInflater) {
        layoutInflater.inflate(R.layout.v__search_route, this);
    }

    @Override
    void setupViews() {
        itemsView.setLayoutManager(new LinearLayoutManager(getContext()));
        itemsView.setAdapter(new Adapter(layoutInflater, miscFunctions, resources));
    }

    @Override
    Disposable subscribeForEvents() {
        return new CompositeDisposable(
            subscribeForRoutes(),
            subscribeForSelection()
        );
    }

    private Disposable subscribeForRoutes() {
        return Observable
            .combineLatest(
                presenterStore.getRoutesPresenter(this).getResults()
                    .filter(Result::isSuccess)
                    .map(Result::getData),
                presenterStore.getSelectedRoutesPresenter(this).getSelectedRoutes(),
                RxTextView.textChanges(inputView)
                    .map(text -> text.toString().toLowerCase()),
                (groups, routeIds, search) -> {
                    if (search.length() == 0) {
                        return Collections.<RouteInfo>emptyList();
                    }
                    List<RouteInfo> routes = new ArrayList<>();
                    for (RouteGroup group: groups) {
                        for (Route route: group.routes()) {
                            if ((isRouteGroupMatchesSearch(group, search) || isRouteMatchesSearch(route, search)) && !routeIds.contains(route.id())) {
                                routes.add(new RouteInfo(group, route));
                            }
                        }
                    }
                    return routes;
                }
            )
            .compose(commonFunctions.toMainThread())
            .subscribe(((Adapter) itemsView.getAdapter())::setRoutes);
    }

    private boolean isRouteGroupMatchesSearch(RouteGroup group, CharSequence search) {
        return miscFunctions.getRouteGroupName(group).toString().toLowerCase().contains(search);
    }

    private boolean isRouteMatchesSearch(Route route, CharSequence search) {
        return route.number().contains(search) ||
            route.source().toLowerCase().contains(search) ||
            route.destination().toLowerCase().contains(search);
    }

    private Disposable subscribeForSelection() {
        return ((Adapter) itemsView.getAdapter()).getSelectRouteRequests()
            .subscribe(o -> inputView.setText(""));
    }

    @Override
    public Observable<Object> getAttaches() {
        return RxView.attaches(this);
    }

    @Override
    public Observable<Object> getDetaches() {
        return RxView.detaches(this);
    }

    @Override
    public boolean isAttached() {
        return true;
    }

    @Override
    public Observable<Object> getLoadRoutesRequests() {
        return Observable.just(Irrelevant.INSTANCE);
    }

    @Override
    public Observable<Collection<String>> getSelectRoutesRequests() {
        return presenterStore.getSelectedRoutesPresenter(this).getSelectedRoutes().switchMap(routeIds ->
            ((Adapter) itemsView.getAdapter()).getSelectRouteRequests().map(routeId -> {
                Collection<String> result = new HashSet<>(routeIds);
                result.add(routeId);
                return result;
            })
        );
    }
}
