(function() {
    'use strict';

    angular.module('theHiveControllers').controller('OrgModalCtrl',
        function($scope, $uibModalInstance, organisation, mode) {
            var self = this;

            this.organisation = organisation;
            this.mode = mode;

            self.initForm = function(org) {
                this.formData = _.defaults(
                    _.pick(org || {}, '_id', 'name', 'description'), {
                        name: null
                    }
                );
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
