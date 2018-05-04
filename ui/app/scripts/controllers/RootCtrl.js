/**
 * Controller for main page
 */
angular.module('theHiveControllers').controller('RootCtrl',
    function($scope, $rootScope, $uibModal, $location, $state, AuthenticationSrv, AlertingSrv, StreamSrv, StreamStatSrv, CaseTemplateSrv, CustomFieldsCacheSrv, MetricsCacheSrv, NotificationSrv, AppLayoutSrv, VersionSrv, currentUser, appConfig) {
        'use strict';

        if(currentUser === 520) {
            $state.go('maintenance');
            return;
        }else if(!currentUser || !currentUser.id) {
            $state.go('login');
            return;
        }

        $rootScope.layoutSrv = AppLayoutSrv;
        $scope.appConfig = appConfig;

        $scope.querystring = '';
        $scope.view = {
            data: 'mytasks'
        };
        $scope.mispEnabled = false;
        $scope.customFieldsCache = [];
        $scope.currentUser = currentUser;

        StreamSrv.init();
        VersionSrv.startMonitoring(function(conf) {
          var connectors = ['misp', 'cortex'];

          _.each(connectors, function(connector) {
              var currentStatus = $scope.appConfig.connectors[connector];
              var newStatus = conf.connectors[connector];
              if(currentStatus.enabled === newStatus.enabled &&
                  newStatus.enabled === true &&
                  currentStatus.status !== newStatus.status) {

                  if(newStatus.status === 'OK') {
                      NotificationSrv.log('The configured ' + connector.toUpperCase() + ' connections are now up.', 'success');
                  } else if(newStatus.status === 'WARNING') {
                      NotificationSrv.log('Some of the configured ' + connector.toUpperCase() + ' connections have errors. Please check your configuration.', 'warning');
                  } else {
                      NotificationSrv.log('The configured ' + connector.toUpperCase() + ' connections have errors. Please check your configuration.', 'error');
                  }
              }
          });

          $scope.appConfig = conf;
        });

        CaseTemplateSrv.list().then(function(templates) {
            $scope.templates = templates;
        });

        $scope.myCurrentTasks = StreamStatSrv({
            scope: $scope,
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
            scope: $scope,
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

        // Get Alert counts
        $scope.alertEvents = AlertingSrv.stats($scope);

        $scope.$on('templates:refresh', function(){
            CaseTemplateSrv.list().then(function(templates) {
                $scope.templates = templates;
            });
        });

        $scope.$on('metrics:refresh', function() {
            // Get metrics cache
            MetricsCacheSrv.all().then(function(list) {
                $scope.metricsCache = list;
            });
        });

        $scope.$on('custom-fields:refresh', function() {
            // Get custom fields cache
            $scope.initCustomFieldsCache();
        });

        $scope.$on('alert:event-imported', function() {
            $scope.alertEvents = AlertingSrv.stats($scope);
        });

        // FIXME
        // $scope.$on('misp:status-updated', function(event, enabled) {
        //     $scope.mispEnabled = enabled;
        // });

        $scope.initCustomFieldsCache = function() {
            CustomFieldsCacheSrv.all().then(function(list) {
                $scope.customFieldsCache = list;
            });
        };
        $scope.initCustomFieldsCache();

        $scope.isAdmin = function(user) {
            var u = user;
            var re = /admin/i;
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
                NotificationSrv.error('RootCtrl', data, status);
            });
        };

        $scope.createNewCase = function(template) {
            $uibModal.open({
                templateUrl: 'views/partials/case/case.creation.html',
                controller: 'CaseCreationCtrl',
                size: 'lg',
                resolve: {
                    template: template
                }
            });
        };

        $scope.aboutTheHive = function() {
            $uibModal.open({
                templateUrl: 'views/partials/about.html',
                controller: 'AboutCtrl',
                size: ''
            });
        };

        $scope.search = function(querystring) {
            var query = Base64.encode(angular.toJson({
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
