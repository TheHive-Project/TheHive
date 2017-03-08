(function() {
    'use strict';

    angular.module('theHiveControllers')
        .controller('CaseReopenModalCtrl', function($scope, $uibModalInstance, AlertSrv) {

            $scope.cancel = function() {
                $uibModalInstance.dismiss();
            };

            $scope.confirm = function() {
                $scope.updateField('status', 'Open')
                    .then(function(caze) {
                        $scope.caze = caze;
                        
                        AlertSrv.log('The case #' + caze.caseId + ' has been reopened', 'success');
                    });
                $uibModalInstance.close();
            };
        });
})();
