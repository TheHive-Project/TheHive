(function () {
    'use strict';

    angular.module('theHiveControllers')
        .controller('CaseReportModalCtrl', CaseReportModalCtrl);

    function CaseReportModalCtrl($scope, $state, $uibModalInstance, $q, PSearchSrv,SearchSrv, CaseSrv, UserInfoSrv, NotificationSrv, caze, $http) {
        var me = this;
        this.caze = caze;
        $scope.artifacts = PSearchSrv(caze.caseId, 'case_artifact', {
                        scope: $scope,
                        baseFilter: {
                            '_and': [{
                                '_parent': {
                                    "_type": "case",
                                    "_query": {
                                        "_id": caze.caseId
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
