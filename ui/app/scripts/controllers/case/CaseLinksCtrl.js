(function() {
    'use strict';
    angular.module('theHiveControllers').controller('CaseLinksCtrl',
        function($scope, $state, $stateParams, $uibModal, $timeout, CaseTabsSrv) {
            $scope.caseId = $stateParams.caseId;
            var tabName = 'links-' + $scope.caseId;


            // Add tab
            CaseTabsSrv.addTab(tabName, {
                name: tabName,
                label: 'Links',
                closable: true,
                state: 'app.case.links',
                params: {}
            });

            // Select tab
            $timeout(function() {
                CaseTabsSrv.activateTab(tabName);
            }, 0);
        }
    );
})();
