(function() {
    'use strict';

    angular.module('theHiveControllers').controller('ProfileListCtrl',
        function($uibModal, ProfileSrv, NotificationSrv) {
            var self = this;

            self.load = function() {
                ProfileSrv.list()
                    .then(function(response) {
                        self.list = response.data;
                    })
                    .catch(function() {
                        // TODO: Handle error
                    });
            };

            self.showProfile = function(mode, profile) {
                var modal = $uibModal.open({
                    controller: 'ProfileModalCtrl',
                    controllerAs: '$modal',
                    templateUrl: 'views/partials/admin/profile/profile.modal.html',
                    size: 'lg',
                    resolve: {
                        profile: profile,
                        mode: function(){
                            return mode;
                        }
                    }
                });

                modal.result
                    .then(function(profile) {
                        if (mode === 'edit') {
                            self.update(profile.id, profile);
                        } else {
                            self.create(profile);
                        }
                    })
                    .catch(function(err){
                        if (err && !_.isString(err)) {
                            NotificationSrv.error('Unable to save the organisation.');
                        }
                    });
            };

            self.update = function(id, profile) {
                ProfileSrv.update(id, _.pick(profile, 'permissions'))
                    .then(function(/*response*/) {
                        self.load();
                        NotificationSrv.log('Profile updated successfully', 'success');
                    })
                    .catch(function(err) {
                        NotificationSrv.error('Error', 'Profile update failed', err.status);
                    });
            };

            self.create = function(profile) {
                ProfileSrv.create(profile)
                    .then(function(/*response*/) {
                        self.load();
                        NotificationSrv.log('Profile created successfully', 'success');
                    })
                    .catch(function(err) {
                        NotificationSrv.error('Error', 'Profile creation failed', err.status);
                    });
            };

            self.load();
        });
})();
