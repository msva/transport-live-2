package micdm.transportlive2.data.loaders.remote;

import android.os.Build;

import com.google.gson.Gson;
import com.jakewharton.retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

import org.joda.time.LocalDateTime;
import org.joda.time.Period;

import dagger.Module;
import dagger.Provides;
import micdm.transportlive2.AppScope;
import micdm.transportlive2.BuildConfig;
import micdm.transportlive2.ComponentHolder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.CallAdapter;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import timber.log.Timber;

@Module
public class RemoteModule {

    @Provides
    @AppScope
    ServerConnector provideServerConnector() {
        ServerConnector instance = new ServerConnector();
        ComponentHolder.getAppComponent().inject(instance);
        return instance;
    }

    @Provides
    @AppScope
    ApiService provideApiService(Retrofit retrofit) {
        return retrofit.create(ApiService.class);
    }

    @Provides
    @AppScope
    Retrofit provideRetrofit(OkHttpClient okHttpClient, Converter.Factory converterFactory, CallAdapter.Factory callAdapterFactory) {
        return new Retrofit.Builder()
            .baseUrl("http://gpt.incom.tomsk.ru/api/")
            .client(okHttpClient)
            .addConverterFactory(converterFactory)
            .addCallAdapterFactory(callAdapterFactory)
            .build();
    }

    @Provides
    @AppScope
    OkHttpClient provideOkHttpClient() {
        return new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                LocalDateTime now = LocalDateTime.now();
                Response response = chain.proceed(chain.request());
                Timber.d("Request %s completed with status %s in %sms", response.request().url(), response.code(), new Period(now, LocalDateTime.now()).getMillis());
                return response;
            })
            .addInterceptor(chain -> {
                Request request = chain.request().newBuilder()
                    .addHeader("User-Agent", String.format("%s %s @ Android %s", BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME, Build.VERSION.RELEASE))
                    .build();
                return chain.proceed(request);
            })
            .build();
    }

    @Provides
    @AppScope
    Converter.Factory provideConverterFactory(Gson gson) {
        return GsonConverterFactory.create(gson);
    }

    @Provides
    @AppScope
    CallAdapter.Factory provideCallAdapterFactory() {
        return RxJava2CallAdapterFactory.create();
    }
}
