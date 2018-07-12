(function () {
    'use strict';

    angular.module('theHiveControllers')
        .controller('CaseReportModalCtrl', CaseReportModalCtrl);

    function CaseReportModalCtrl($scope, $state, $uibModalInstance, $q, PSearchSrv,SearchSrv, CaseSrv, UserInfoSrv, NotificationSrv, caze, $http) {
        $scope.caze = caze;
        console.log($scope)
        $scope.artifacts = PSearchSrv($scope.caseId, 'case_artifact', {
                        scope: $scope,
                        baseFilter: {
                            '_and': [{
                                '_parent': {
                                    "_type": "case",
                                    "_query": {
                                        "_id": $scope.caseId
                                    }
                                }
                            },   {
                                'status': 'Ok'
                            }]
                        },
                        loadAll: true,
                        sort: '-startDate',
                        nstats: true
        });

        this.cancel = function () {
            $uibModalInstance.dismiss();
        };
    }
})();
