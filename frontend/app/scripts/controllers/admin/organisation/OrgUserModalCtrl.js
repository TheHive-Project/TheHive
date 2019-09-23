(function() {
    'use strict';

    angular.module('theHiveControllers').controller('OrgUserModalCtrl', function($scope, $uibModalInstance, UserSrv, NotificationSrv, organisation, user, profiles, isEdit) {
        var self = this;

        self.user = user;
        self.isEdit = isEdit;
        self.profiles = profiles;
        self.organisation = organisation;

        var formData = _.defaults(_.pick(self.user, '_id', 'name', 'login', 'profile', 'organisation'), {
            _id: null,
            login: null,
            name: null,
            profile: null,
            organisation: self.organisation
        });

        self.formData = formData;

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

            if (self.user._id) {
                postData = {
                    name: self.formData.name,
                    login: self.formData.login.toLowerCase(),
                    profile: self.formData.profile.name,
                    organisation: self.formData.organisation
                };
                UserSrv.update(self.user._id, postData)
                    .then(onSuccess)
                    .catch(onFailure);
            } else {
                postData = {
                    login: self.formData.login.toLowerCase(),
                    name: self.formData.name,
                    profile: self.formData.profile.name,
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
