(function() {
    'use strict';

    angular.module('theHiveControllers').controller('OrgUserModalCtrl', function($scope, $uibModalInstance, OrganisationSrv, UserSrv, NotificationSrv, organisation, user, profiles, isEdit) {
        var self = this;

        self.user = user;
        self.isEdit = isEdit;
        self.organisation = organisation;

        self.$onInit = function() {

            // filter profiles based organisation
            if(OrganisationSrv.isDefaultOrg({name: self.organisation})) {

                self.profiles = _.indexBy(_.filter(_.values(profiles), function(profile) {
                    return !!profile.isAdmin;
                }), 'name');
            } else {
                self.profiles = _.indexBy(_.filter(_.values(profiles), function(profile) {
                    return !!!profile.isAdmin;
                }), 'name');
            }

            var formData = _.defaults(_.pick(self.user, '_id', 'name', 'login', 'organisation'), {
                _id: null,
                login: null,
                name: null,
                organisation: self.organisation
            });

            formData.profile = self.user.profile ? self.profiles[self.user.profile] : undefined;

            self.formData = formData;
        };

        var onSuccess = function(data) {
            $uibModalInstance.close(data);
        };

        var onFailure = function(response) {
            NotificationSrv.error('OrgUserModalCtrl', response.data, response.status);
        };

        self.saveUser = function(form) {
            if (!form.$valid) {
                return;
            }

            var postData = {};

            var profile = (self.organisation === 'admin') ? 'admin' : self.formData.profile.name;

            if (self.user._id) {
                postData = {
                    name: self.formData.name,
                    profile: profile,
                    organisation: self.formData.organisation
                };
                UserSrv.update(self.user._id, postData)
                    .then(onSuccess)
                    .catch(onFailure);
            } else {
                postData = {
                    login: self.formData.login.toLowerCase(),
                    name: self.formData.name,
                    profile: profile,
                    organisation: self.formData.organisation
                };
                UserSrv.save(postData)
                    .then(onSuccess)
                    .catch(onFailure);
            }
        };

        self.cancel = function() {
            $uibModalInstance.dismiss();
        };

    });
})();
