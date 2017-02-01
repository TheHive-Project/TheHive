(function() {
    'use strict';
    angular.module('theHiveControllers').controller('MainPageCtrl',
        function($rootScope, $scope, $window, $stateParams, $state, CaseTaskSrv, PSearchSrv, EntitySrv, UserInfoSrv) {

            $scope.live = function() {
                $window.open($state.href('live'), 'TheHiveLive',
                    'width=500,height=700,menubar=no,status=no,toolbar=no,location=no,scrollbars=yes');
            };

            if ($stateParams.viewId === 'mytasks') {
                $rootScope.title = 'My tasks';
                $scope.view.data = 'mytasks';
                $scope.list = PSearchSrv(undefined, 'case_task', {
                    'baseFilter': {
                        '_and': [{
                            'status': 'InProgress'
                        }, {
                            'owner': $scope.currentUser.id
                        }]
                    },
                    'sort': ['-flag', '-startDate'],
                    'nparent': 1
                });

            } else if ($stateParams.viewId === 'waitingtasks') {
                $rootScope.title = 'Waiting tasks';
                $scope.view.data = 'waitingtasks';
                $scope.list = PSearchSrv(undefined, 'case_task', {
                    'baseFilter': {
                        'status': 'Waiting'
                    },
                    'sort': '-startDate',
                    'nparent': 1
                });
            } else if ($stateParams.viewId === 'closedcases') {
                $rootScope.title = 'Closed cases';
                $scope.view.data = 'closedcases';
                $scope.list = PSearchSrv(undefined, 'case', {
                    'baseFilter': {
                        '_and': [{
                            'status': 'Resolved'
                        }, {
                            '_between': {
                                '_field': 'endDate',
                                '_from': 'now-14d',
                                '_to': 'now'
                            }
                        }]
                    },
                    'sort': '-startDate',
                    'nstats': true
                });
            } else {
                $rootScope.title = 'Current cases';
                $scope.view.data = 'currentcases';
                $scope.list = PSearchSrv(undefined, 'case', {
                    'baseFilter': {
                        'status': 'Open'
                    },
                    'sort': ['-flag', '-startDate'],
                    'nstats': true
                });
            }

            // init values
            $scope.showFlow = true;
            $scope.openEntity = EntitySrv.open;
            $scope.getUserInfo = UserInfoSrv;

            $scope.openWTask = function(task) {
                if (task.status === 'Waiting') {
                    CaseTaskSrv.update({
                        'taskId': task.id
                    }, {
                        'status': 'InProgress'
                    }, function(data) {
                        if (data.status === 'InProgress') {
                            $scope.openEntity(task);
                        }
                    }, function(response) {
                        console.log(response);
                    });
                }
            };
        }
    );
})();
