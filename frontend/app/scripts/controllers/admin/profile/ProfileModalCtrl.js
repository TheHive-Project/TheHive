(function() {
    'use strict';

    angular.module('theHiveControllers').controller('ProfileModalCtrl',
        function($scope,$uibModalInstance, ProfileSrv, profile, mode) {
            var self = this;

            this.permissions = ProfileSrv.permissions;
            this.profile = profile || {};
            this.mode = mode;
            this.isEdit = !!this.profile.id;

            self.initForm = function(profile) {
                self.formData = _.defaults(
                    _.pick(profile || {}, 'id', 'name', 'permissions', 'isAdmin'), {
                        name: null,
                        permissions: [],
                        isAdmin: false
                    }
                );
            };

            self.ok = function() {
                $uibModalInstance.close(self.formData);
            };

            this.cancel = function() {
                $uibModalInstance.dismiss('cancel');
            };

            this.initForm(this.profile);
        });
})();
