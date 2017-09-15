(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('ServerInstanceDialogCtrl', ServerInstanceDialogCtrl);

        function ServerInstanceDialogCtrl($uibModalInstance, servers) {
            var self = this;
            
            this.servers = servers;
            this.selected = null;

            this.ok = function() {
                $uibModalInstance.close(this.selected);
            };

            this.cancel = function() {
                $uibModalInstance.dismiss();
            };
        }        
})();
