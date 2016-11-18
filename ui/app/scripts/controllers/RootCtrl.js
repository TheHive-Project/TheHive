/**
 * Controller for main page
 */
angular.module('theHiveControllers').controller('RootCtrl',
    function($scope, $modal, $location, $state, $base64, AuthenticationSrv, MispSrv, StreamSrv, StreamStatSrv, TemplateSrv, MetricsCacheSrv, AlertSrv) {
        'use strict';

        $scope.querystring = '';
        $scope.view = {
            data: 'currentcases'
        };
        $scope.mispEnabled = false;

        StreamSrv.init();
        $scope.currentUser = AuthenticationSrv.current(function() {
            // while succeed get myCurrentTasks stats

            $scope.templates = TemplateSrv.query();

            $scope.myCurrentTasks = StreamStatSrv({
                rootId: 'any',
                query: {
                    '_and': [{
                        'status': 'InProgress'
                    }, {
                        'owner': $scope.currentUser.id
                    }]
                },
                result: {},
                objectType: 'case_task',
                field: 'status'
            });

            $scope.waitingTasks = StreamStatSrv({
                rootId: 'any',
                query: {
                    'status': 'Waiting'
                },
                result: {},
                objectType: 'case_task',
                field: 'status'
            });

            // Get metrics cache
            MetricsCacheSrv.all().then(function(list) {
                $scope.metricsCache = list;
            });

            // Get MISP counts
            $scope.mispEvents = MispSrv.stats();
        }, function(data, status) {
            AlertSrv.error('RootCtrl', data, status);
        });

        $scope.$on('metrics:refresh', function() {
            // Get metrics cache
            MetricsCacheSrv.all().then(function(list) {
                $scope.metricsCache = list;
            });
        });

        $scope.$on('misp:status-updated', function(event, enabled) {
            $scope.mispEnabled = enabled;
        });

        $scope.isAdmin = function(user) {
            var u = user;
            var re = /admin/;
            return re.test(u.roles);
        };

        $scope.selectView = function(name) {
            $state.go('app.main', {
                viewId: name
            });
            $scope.view.data = name;
        };

        $scope.logout = function() {
            AuthenticationSrv.logout(function() {
                $state.go('login');
            }, function(data, status) {
                AlertSrv.error('RootCtrl', data, status);
            });
        };

        $scope.createNewCase = function(template) {
            $modal.open({
                templateUrl: 'views/partials/case/case.creation.html',
                controller: 'CaseCreationCtrl',
                size: 'lg',
                resolve: {
                    template: template
                }
            });
        };

        $scope.aboutTheHive = function() {
            $modal.open({
                templateUrl: 'views/partials/about.html',
                controller: 'AboutCtrl',
                size: ''
            });
        };

        $scope.search = function(querystring) {
            var query = $base64.encode(angular.toJson({
                _string: querystring
            }));

            $state.go('app.search', {
                q: query
            });
        };

        // Used to show spinning refresh icon n times
        $scope.getNumber = function(num) {
            return new Array(num);
        };
    }
);
