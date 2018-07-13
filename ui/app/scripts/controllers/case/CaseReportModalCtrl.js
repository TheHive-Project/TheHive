(function () {
    'use strict';

    angular.module('theHiveControllers')
        .controller('CaseReportModalCtrl', CaseReportModalCtrl);

    function CaseReportModalCtrl($scope, $state, $uibModalInstance, $q, $compile, PSearchSrv,SearchSrv, CaseSrv, UserInfoSrv, NotificationSrv, caze, $http, CaseReportSrv) {
        var self = this;
        self.templates = [];
        $scope.caze = caze;

        $q.all([
          CaseReportSrv.list(),
        ]).then(function (response) {
          self.templates = response[0];
          self.template = self.templates[0].content;
          $('#case-report-content').html($compile(self.template)($scope));
          return $q.resolve(self.template);
        }, function(rejection) {
          NotificationSrv.error('CaseReport', rejection.data, rejection.status);
        });


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

        $scope.createPDF = function(){
            console.log('Creating PDF');
            var doc = new jsPDF();

            // We'll make our own renderer to skip this editor
            var specialElementHandlers = {
            	'#editor': function(element, renderer){
            		return true;
            	},
            	'.controls': function(element, renderer){
            		return true;
            	}
            };

            // All units are in the set measurement for the document
            // This can be changed to "pt" (points), "mm" (Default), "cm", "in"
            doc.fromHTML($('#case-report-content').get(0), 10, 10, {
            	'width': 170,
            	'elementHandlers': specialElementHandlers
            });
            doc.save('test.pdf');
        }

        $scope.cancel = function () {
            $uibModalInstance.dismiss();
        };
    }
})();
