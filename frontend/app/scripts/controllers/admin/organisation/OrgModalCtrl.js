(function() {
    'use strict';

    angular.module('theHiveControllers').controller('OrgModalCtrl',
        function($scope, $uibModalInstance, OrganisationSrv, organisation, mode) {
            var self = this;

            this.organisation = organisation;
            this.mode = mode;

            self.initForm = function(org) {
                self.formData = _.defaults(
                    _.pick(org || {}, '_id', 'name', 'description'), {
                        name: null
                    }
                );

                self.nameIsEditable = !!!self.formData._id || self.formData.name !== OrganisationSrv.defaultOrg;
            };



            self.ok = function() {
                $uibModalInstance.close(self.formData);
            };

            this.cancel = function() {
                $uibModalInstance.dismiss('cancel');
            };

            this.initForm(this.organisation);
        });
})();
