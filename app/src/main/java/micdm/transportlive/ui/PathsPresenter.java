package micdm.transportlive.ui;

import java.util.ArrayList;
import java.util.Collection;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import micdm.transportlive.data.BaseLoader;
import micdm.transportlive.data.Loaders;
import micdm.transportlive.data.PathLoader;
import micdm.transportlive.misc.CommonFunctions;
import micdm.transportlive.models.Path;

public class PathsPresenter extends BasePresenter<PathsPresenter.View> implements PathLoader.Client {

    interface View extends BasePresenter.View {

        Observable<Collection<String>> getLoadPathsRequests();
        Observable<Collection<String>> getReloadPathsRequests();
    }

    @Inject
    CommonFunctions commonFunctions;
    @Inject
    Loaders loaders;

    @Override
    Disposable subscribeForEvents() {
        return new CompositeDisposable(
            getPathsToLoad().subscribe(routeIds -> {
                for (String routeId: routeIds) {
                    loaders.getPathLoader(routeId).attach(this);
                }
            }),
            getPathsToCancelLoad().subscribe(routeIds -> {
                for (String routeId: routeIds) {
                    loaders.getPathLoader(routeId).detach(this);
                }
            })
        );
    }

    private Observable<Collection<String>> getPathsToLoad() {
        return getViewInput(View::getLoadPathsRequests);
    }

    private Observable<Collection<String>> getPathsToReload() {
        return getViewInput(View::getReloadPathsRequests);
    }

    private Observable<Collection<String>> getPathsToCancelLoad() {
        return commonFunctions.getDelta(
            getViewInput(View::getLoadPathsRequests).skip(1),
            getViewInput(View::getLoadPathsRequests).compose(commonFunctions.getPrevious())
        );
    }

    @Override
    public Observable<String> getLoadPathRequests() {
        return getPathsToLoad().switchMap(Observable::fromIterable);
    }

    @Override
    public Observable<String> getReloadPathRequests() {
        return getPathsToReload().switchMap(Observable::fromIterable);
    }

    @Override
    public Observable<String> getCancelPathLoadingRequests() {
        return getPathsToCancelLoad().switchMap(Observable::fromIterable);
    }

    Observable<BaseLoader.Result<Collection<Path>>> getResults() {
        return getPathsToLoad().switchMap(routeIds -> {
            Collection<Observable<BaseLoader.Result<Path>>> observables = new ArrayList<>(routeIds.size());
            for (String routeId: routeIds) {
                observables.add(loaders.getPathLoader(routeId).getData());
            }
            return Observable.combineLatest(observables, objects -> {
                Collection<BaseLoader.Result<Path>> results = new ArrayList<>(objects.length);
                for (Object result: objects) {
                    results.add((BaseLoader.Result<Path>) result);
                }
                for (BaseLoader.Result<Path> result: results) {
                    if (result.isFail()) {
                        return BaseLoader.Result.newFail();
                    }
                }
                for (BaseLoader.Result<Path> result: results) {
                    if (result.isLoading()) {
                        return BaseLoader.Result.newLoading();
                    }
                }
                Collection<Path> paths = new ArrayList<>();
                for (BaseLoader.Result<Path> result: results) {
                    paths.add(result.getData());
                }
                return BaseLoader.Result.newSuccess(paths);
            });
        });
    }
}
