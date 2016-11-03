(function() {
    'use strict';

    angular.module('theHiveControllers')
        .controller('CaseReopenModalCtrl', function($scope, $modalInstance, AlertSrv) {

            $scope.cancel = function() {
                $modalInstance.dismiss();
            };

            $scope.confirm = function() {
                $scope.updateField('status', 'Open')
                    .then(function(caze) {
                        $scope.caze = caze;
                        
                        AlertSrv.log('The case #' + caze.caseId + ' has been reopened', 'success');
                    });
                $modalInstance.close();
            };
        });
})();
