(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('SearchCtrl', function($scope, $stateParams, $uibModal, PSearchSrv, CaseTemplateSrv, CaseTaskSrv, NotificationSrv, EntitySrv, UserInfoSrv) {
            $scope.filter = {
                type: {
                    values: [
                        {
                            id: 'case',
                            label: 'Case'
                        },
                        {
                            id: 'case_task',
                            label: 'Task'
                        },
                        {
                            id: 'case_task_log',
                            label: 'Task log'
                        },
                        {
                            id: 'case_artifact',
                            label: 'Observable'
                        },
                        {
                            id: 'case_artifact_job',
                            label: 'Analyzer Job'
                        },
                        {
                            id: 'analyzer',
                            label: 'Analyzer'
                        }
                    ],
                    selection: [],
                    config: {
                        smartButtonMaxItems: 10,
                        smartButtonTextConverter: function(itemText) {
                            return itemText;
                        }
                    }
                }
            };

            $scope.getUserInfo = UserInfoSrv;
            $scope.searchResults = PSearchSrv(undefined, 'any', {
                'filter': angular.fromJson(Base64.decode($stateParams.q)),
                'baseFilter': {_string: '!_type:dashboard AND !_type:audit AND !_type:data AND !_type:user AND !_type:analyzer AND !_type:case_artifact_job_log AND !status:Deleted'},
                'nparent': 10,
                skipStream: true
            });

            $scope.openEntity = EntitySrv.open;
            $scope.isImage = function(contentType) {
                return angular.isString(contentType) && contentType.indexOf('image') === 0;
            };

            $scope.importAlert = function(event) {
                $uibModal.open({
                    templateUrl: 'views/partials/alert/event.dialog.html',
                    controller: 'AlertEventCtrl',
                    controllerAs: 'dialog',
                    size: 'max',
                    resolve: {
                        event: event,
                        templates: function() {
                            return CaseTemplateSrv.list();
                        }
                    }
                }).result.then(function(response) {
                  $scope.searchResults.update();
                });
            };
        });
})();
