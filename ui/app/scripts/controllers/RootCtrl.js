/**
 * Controller for main page
 */
angular.module('theHiveControllers').controller('RootCtrl',
    function($scope, $rootScope, $uibModal, $location, $state, AuthenticationSrv, AlertingSrv, StreamSrv, StreamStatSrv, CaseSrv, CaseTemplateSrv, CustomFieldsCacheSrv, MetricsCacheSrv, NotificationSrv, AppLayoutSrv, VersionSrv, currentUser, appConfig) {
        'use strict';

        if(currentUser === 520) {
            $state.go('maintenance');
            return;
        }else if(!currentUser || !currentUser.id) {
            $state.go('login', {autoLogin: appConfig.config.ssoAutoLogin });
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
              if(currentStatus && currentStatus.enabled === newStatus.enabled &&
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
                '_and': [
                    {
                        '_in': {
                            '_field': 'status',
                            '_values': ['Waiting', 'InProgress']
                        }
                    },
                    {
                        'owner': $scope.currentUser.id
                    }
                ]
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
            var modal = $uibModal.open({
                templateUrl: 'views/partials/case/case.creation.html',
                controller: 'CaseCreationCtrl',
                size: 'lg',
                resolve: {
                    template: template
                }
            });

            modal.result
                .then(function(data) {
                    $state.go('app.case.details', {
                        caseId: data.id
                    });
                })
                .catch(function(err) {
                    if(err && !_.isString(err)) {
                        NotificationSrv.error('CaseCreationCtrl', err.data, err.status);
                    }
                });
        };

        $scope.openTemplateSelector = function() {
            var modal = $uibModal.open({
                templateUrl: 'views/partials/case/case.templates.selector.html',
                controller: 'CaseTemplatesDialogCtrl',
                controllerAs: 'dialog',
                size: 'lg',
                resolve: {
                    templates: function(){
                        return $scope.templates;
                    }
                }
            });

            modal.result.then(function(template) {
                $scope.createNewCase(template);
            })
        };

        $scope.aboutTheHive = function() {
            $uibModal.open({
                templateUrl: 'views/partials/about.html',
                controller: 'AboutCtrl',
                size: ''
            });
        };

        $scope.search = function(caseId) {
            if(!caseId || !_.isNumber(caseId) || caseId <= 0) {
                return;
            }

            CaseSrv.query({
                query: {
                    caseId: caseId
                },
                range: '0-1'
            }, function(response) {
                if(response.length === 1) {
                    $state.go('app.case.details', {caseId: response[0].id}, {reload: true});
                } else {
                    NotificationSrv.log('Unable to find the case with number ' + caseId, 'error');
                }
                console.log(response[0]);
            }, function(err) {
                NotificationSrv.error('Case search', err.data, err.status);
            });
        };

        // Used to show spinning refresh icon n times
        $scope.getNumber = function(num) {
            return new Array(num);
        };
    }
);
