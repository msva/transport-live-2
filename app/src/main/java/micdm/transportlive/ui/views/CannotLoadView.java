package micdm.transportlive.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

import com.jakewharton.rxbinding2.view.RxView;

import butterknife.BindView;
import io.reactivex.Observable;
import micdm.transportlive.ComponentHolder;
import micdm.transportlive.R;

public class CannotLoadView extends BaseView {

    @BindView(R.id.f__cannot_load__retry)
    View retryView;

    public CannotLoadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (!isInEditMode()) {
            ComponentHolder.getActivityComponent().inject(this);
        }
    }

    @Override
    protected void inflateContent(LayoutInflater layoutInflater) {
        layoutInflater.inflate(R.layout.v__cannot_load, this);
    }

    public Observable<Object> getRetryRequest() {
        return RxView.clicks(retryView);
    }
}
