(function () {
    'use strict';

    angular.module('theHiveControllers').controller('OrgModalCtrl',
        function ($scope, $uibModalInstance, OrganisationSrv, SharingProfileSrv, organisation, mode) {
            var self = this;

            this.organisation = organisation;
            this.mode = mode;
            this.sharingRules = SharingProfileSrv.SHARING_RULES;

            self.initForm = function (org) {
                self.formData = _.defaults(
                    _.pick(org || {}, '_id', 'name', 'description', 'taskRule', 'observableRule'),
                    {
                        name: null,
                        taskRule: 'manual',
                        observableRule: 'manual'
                    }
                );

                self.nameIsEditable = !!!self.formData._id || self.formData.name !== OrganisationSrv.defaultOrg;
            };

            self.ok = function () {
                $uibModalInstance.close(self.formData);
            };

            this.cancel = function () {
                $uibModalInstance.dismiss('cancel');
            };

            this.initForm(this.organisation);
        });
})();
