(function () {
    'use strict';

    angular.module('theHiveControllers')
        .controller('CaseReportModalCtrl', CaseReportModalCtrl);

    function CaseReportModalCtrl($scope, $state, $uibModalInstance, $q, $compile, PSearchSrv,SearchSrv, CaseSrv, UserInfoSrv, NotificationSrv, caze, $http, CaseReportSrv) {
        var self = this;
        self.templates = [];
        self.artifacts = [];

        $q.all([
          CaseReportSrv.list(),
          PSearchSrv($scope.caseId, 'case_artifact', {
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
                  }),
        ]).then(function (response) {
          self.templates = response[0];
          caze.artifacts = response[1];
          console.log(caze.artifacts);
          var template = self.templates[0].content;
          $('#case-report-content').html($compile(template)($scope));
          return $q.resolve(self.template);
        }, function(rejection) {
          NotificationSrv.error('CaseReport', rejection.data, rejection.status);
        });

        $scope.createPDF = function(){
          var pdf = new jsPDF('p', 'pt', 'letter');
          pdf.fromHTML($('#case-report-content').get(0), 5, 5);
          pdf.save(caze.title + '.pdf');
        }

        $scope.cancel = function () {
          $uibModalInstance.dismiss();
        };
    }
})();
