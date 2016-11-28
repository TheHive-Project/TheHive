(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('CaseTaskDeleteCtrl', CaseTaskDeleteCtrl)
        .controller('CaseTasksCtrl', CaseTasksCtrl);

    function CaseTasksCtrl($scope, $state, $stateParams, $modal, CaseTabsSrv, PSearchSrv, CaseTaskSrv, UserInfoSrv, AlertSrv) {

        CaseTabsSrv.activateTab($state.current.data.tab);

        $scope.caseId = $stateParams.caseId;
        $scope.state = {
            'isNewTask': false
        };
        $scope.newTask = {
            status: 'Waiting'
        };

        $scope.tasks = PSearchSrv($scope.caseId, 'case_task', {
            'baseFilter': {
                '_and': [{
                    '_parent': {
                        '_type': 'case',
                        '_query': {
                            '_id': $scope.caseId
                        }
                    }
                }, {
                    '_not': {
                        'status': 'Cancel'
                    }
                }]
            },
            'sort': ['-flag', '+startDate', '+title']
        });

        $scope.showTask = function(task) {
            $state.go('app.case.tasks-item', {
                itemId: task.id
            });
        };

        $scope.addTask = function() {
            CaseTaskSrv.save({
                'caseId': $scope.caseId,
                'flag': false
            }, $scope.newTask, function() {
                $scope.isNewTask = false;
                $scope.newTask.title = '';
            }, function(response) {
                AlertSrv.error('taskList', response.data, response.status);
            });
        };

        $scope.removeTask = function(task) {

            var modalInstance = $modal.open({
                animation: true,
                templateUrl: 'views/partials/case/case.task.delete.html',
                controller: 'CaseTaskDeleteCtrl',
                controllerAs: 'vm',
                resolve: {
                    title: function() {
                        return task.title;
                    }
                }
            });

            modalInstance.result.then(function() {
                CaseTaskSrv.update({
                    'taskId': task.id
                }, {
                    status: 'Cancel'
                }, function() {
                    $scope.$emit('tasks:task-removed', task);
                }, function(response) {
                    AlertSrv.error('taskList', response.data, response.status);
                });
            });

        };

        // open task tab with its details
        $scope.openTask = function(task) {
            if (task.status === 'Waiting') {
                CaseTaskSrv.update({
                    'taskId': task.id
                }, {
                    'status': 'InProgress'
                }, function(data) {
                    $scope.showTask(data);
                }, function(response) {
                    AlertSrv.error('taskList', response.data, response.status);
                });
            } else {
                $scope.showTask(task);
            }
        };

    }

    function CaseTaskDeleteCtrl($modalInstance, title) {
        this.title = title;

        this.ok = function() {
            $modalInstance.close();
        };

        this.cancel = function() {
            $modalInstance.dismiss();
        };
    }
}());
