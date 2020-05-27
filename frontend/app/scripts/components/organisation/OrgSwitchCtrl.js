(function() {
    'use strict';

    angular.module('theHiveControllers').controller('OrgSwitchCtrl',
        function($uibModalInstance, currentUser) {
            //var self = this;

            this.currentUser = currentUser;

            this.selectOrg = function(selected) {
                $uibModalInstance.close(selected);
            };

            this.cancel = function() {
                $uibModalInstance.dismiss();
            };        
        });
})();
