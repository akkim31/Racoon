package com.suzei.racoon.profile.data;

import com.suzei.racoon.model.Users;
import com.suzei.racoon.profile.data.ProfileContract;
import com.suzei.racoon.profile.data.ProfileInteractor;

public class ProfilePresenter implements ProfileContract.onProfileListener {

    private ProfileContract.ProfileView profileView;
    private ProfileInteractor profileInteractor;

    public ProfilePresenter(ProfileContract.ProfileView profileView) {
        this.profileView = profileView;
        profileInteractor = new ProfileInteractor(this);
    }

    public void showUserDetails(String uid) {
        profileView.showProgress();
        profileInteractor.loadDetails(uid);
    }

    public void destroy(String uid) {
        profileInteractor.destroy(uid);
    }

    @Override
    public void onSuccess(Users users) {
        profileView.hideProgress();
        profileView.onLoadSuccess(users);
    }

    @Override
    public void onFailure() {
        profileView.hideProgress();
        profileView.onLoadingFailure();
    }

}