(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('CortexInstanceDialogCtrl', CortexInstanceDialogCtrl);

        function CortexInstanceDialogCtrl($modalInstance, servers) {
            var self = this;
            
            this.servers = servers;
            this.selected = null;

            this.ok = function() {
                $modalInstance.close(this.selected);
            };

            this.cancel = function() {
                $modalInstance.dismiss();
            };
        }        
})();
