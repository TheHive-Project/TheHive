
(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('AlertListCtrl', function($rootScope, $scope, $q, $state, $uibModal, TagSrv, StreamQuerySrv, CaseTemplateSrv, ModalUtilsSrv, AlertingSrv, NotificationSrv, FilteringSrv, CortexSrv, Severity, VersionSrv) {
            var self = this;

            self.urls = VersionSrv.mispUrls();

            self.list = [];
            self.selection = [];
            self.menu = {
                follow: false,
                unfollow: false,
                markAsRead: false,
                markAsUnRead: false,
                delete: false,
                selectAll: false
            };

            self.lastSearch = null;
            self.responders = null;

            this.$onInit = function() {
                self.filtering = new FilteringSrv('alert', 'alert.list', {
                    version: 'v1',
                    defaults: {
                        showFilters: true,
                        showStats: false,
                        pageSize: 15,
                        sort: ['-date']
                    },
                    defaultFilter: [{
                        field: 'imported',
                        type: 'boolean',
                        value: false
                    }]
                });
                self.filtering.initContext('list')
                    .then(function() {
                        self.load();

                        $scope.$watch('$vm.list.pageSize', function (newValue) {
                            self.filtering.setPageSize(newValue);
                        });
                    });

                StreamQuerySrv('v1', [
                    {_name: 'listAlert'},
                    {_name: 'count'}
                ], {
                    scope: $scope,
                    rootId: 'any',
                    objectType: 'alert',
                    query: {
                        params: {
                            name: 'alert-count'
                        }
                    },
                    onUpdate: function(data) {
                        self.alertListCount = data;
                    }
                });
            };

            self.load = function() {
                var config = {
                    scope: $scope,
                    filter: this.filtering.buildQuery(),
                    loadAll: false,
                    sort: self.filtering.context.sort,
                    pageSize: self.filtering.context.pageSize,
                };

                self.list = AlertingSrv.list(config, self.resetSelection);
            };

            this.toggleStats = function () {
                this.filtering.toggleStats();
            };

            this.toggleFilters = function () {
                this.filtering.toggleFilters();
            };

            this.canMarkAsRead = AlertingSrv.canMarkAsRead;
            this.canMarkAsUnread = AlertingSrv.canMarkAsUnread;

            this.markAsRead = function(event) {
                var fn = angular.noop;

                if(this.canMarkAsRead(event)) {
                    fn = AlertingSrv.markAsRead;
                } else {
                    fn = AlertingSrv.markAsUnread;
                }

                fn(event._id).then(function( /*data*/ ) {
                }, function(response) {
                    NotificationSrv.error('AlertListCtrl', response.data, response.status);
                });
            };

            self.follow = function(event) {
                var fn = angular.noop;

                if (event.follow === true) {
                    fn = AlertingSrv.unfollow;
                } else {
                    fn = AlertingSrv.follow;
                }

                fn(event._id).then(function( /*data*/ ) {
                }, function(response) {
                    NotificationSrv.error('AlertListCtrl', response.data, response.status);
                });
            };

            self.bulkFollow = function(follow) {
                var ids = _.pluck(self.selection, 'id');
                var fn = angular.noop;

                if (follow === true) {
                    fn = AlertingSrv.follow;
                } else {
                    fn = AlertingSrv.unfollow;
                }

                var promises = _.map(ids, function(id) {
                    return fn(id);
                });

                $q.all(promises).then(function( /*response*/ ) {
                    NotificationSrv.log('The selected events have been ' + (follow ? 'followed' : 'unfollowed'), 'success');
                }, function(response) {
                    NotificationSrv.error('AlertListCtrl', response.data, response.status);
                });
            };

            self.bulkMarkAsRead = function(markAsReadFlag) {
                var ids = _.pluck(self.selection, '_id');
                var fn = angular.noop;
                var markAsRead = markAsReadFlag && this.canMarkAsRead(self.selection[0]);

                if(markAsRead) {
                    fn = AlertingSrv.markAsRead;
                } else {
                    fn = AlertingSrv.markAsUnread;
                }

                var promises = _.map(ids, function(id) {
                    return fn(id);
                });

                $q.all(promises).then(function( /*response*/ ) {
                    self.list.update();
                    NotificationSrv.log('The selected events have been ' + (markAsRead ? 'marked as read' : 'marked as unread'), 'success');
                }, function(response) {
                    NotificationSrv.error('AlertListCtrl', response.data, response.status);
                });
            };

            self.bulkDelete = function() {

              ModalUtilsSrv.confirm('Remove Alerts', 'Are you sure you want to delete the selected Alerts?', {
                  okText: 'Yes, remove them',
                  flavor: 'danger'
              }).then(function() {
                  var ids = _.pluck(self.selection, '_id');

                  AlertingSrv.bulkRemove(ids)
                      .then(function(/*response*/) {
                          NotificationSrv.log('The selected events have been deleted', 'success');
                      })
                      .catch(function(response) {
                          NotificationSrv.error('AlertListCtrl', response.data, response.status);
                      });
              });
            };

            self.import = function(event) {
                var modalInstance = $uibModal.open({
                    templateUrl: 'views/partials/alert/event.dialog.html',
                    controller: 'AlertEventCtrl',
                    controllerAs: 'dialog',
                    size: 'max',
                    resolve: {
                        event: event,
                        templates: function() {
                            return CaseTemplateSrv.list();
                        },
                        readonly: false
                    }
                });

                modalInstance.result.catch(function(err) {
                    if(err && !_.isString(err)) {
                        NotificationSrv.error('AlertListCtrl', err.data, err.status);
                    }
                });
            };

            self.resetSelection = function() {
                if (self.menu.selectAll) {
                    self.selectAll();
                } else {
                    self.selection = [];
                    self.menu.selectAll = false;
                    self.updateMenu();
                }
            };

            this.getResponders = function(event, force) {
                if(!force && this.responders !== null) {
                   return;
                }

                this.responders = null;
                CortexSrv.getResponders('alert', event._id)
                  .then(function(responders) {
                      self.responders = responders;
                      return CortexSrv.promntForResponder(responders);
                  })
                  .then(function(response) {
                      if(response && _.isString(response)) {
                          NotificationSrv.log(response, 'warning');
                      } else {
                          return CortexSrv.runResponder(response.id, response.name, 'alert', _.pick(event, '_id', 'tlp'));
                      }
                  })
                  .then(function(response){
                      NotificationSrv.log(['Responder', response.data.responderName, 'started successfully on alert', event.title].join(' '), 'success');
                  })
                  .catch(function(err) {
                      if(err && !_.isString(err)) {
                          NotificationSrv.error('AlertList', err.data, err.status);
                      }
                  });
            };

            self.cancel = function() {
                self.modalInstance.close();
            };

            self.updateMenu = function() {
                var temp = _.uniq(_.pluck(self.selection, 'follow'));

                self.menu.unfollow = temp.length === 1 && temp[0] === true;
                self.menu.follow = temp.length === 1 && temp[0] === false;


                temp = _.uniq(_.pluck(self.selection, 'read'));

                self.menu.markAsRead = temp.length === 1 && temp[0] === false;
                self.menu.markAsUnread = temp.length === 1 && temp[0] === true;

                // TODO nadouani: don't rely on alert status
                self.menu.createNewCase = temp.indexOf('Imported') === -1;
                self.menu.mergeInCase = temp.indexOf('Imported') === -1;

                temp = _.without(_.uniq(_.pluck(self.selection, 'case')), null, undefined);

                self.menu.delete = temp.length === 0;
            };

            self.select = function(event) {
                if (event.selected) {
                    self.selection.push(event);
                } else {
                    self.selection = _.reject(self.selection, function(item) {
                        return item._id === event._id;
                    });
                }

                self.updateMenu();

            };

            self.selectAll = function() {
                var selected = self.menu.selectAll;
                _.each(self.list.values, function(item) {
                    item.selected = selected;
                });

                if (selected) {
                    self.selection = self.list.values;
                } else {
                    self.selection = [];
                }

                self.updateMenu();

            };

            self.createNewCase = function() {
                var alertIds = _.pluck(self.selection, '_id');

                CaseTemplateSrv.list()
                  .then(function(templates) {

                      if(!templates || templates.length === 0) {
                          return $q.resolve(undefined);
                      }

                      // Open template selection dialog
                      var modal = $uibModal.open({
                          templateUrl: 'views/partials/case/case.templates.selector.html',
                          controller: 'CaseTemplatesDialogCtrl',
                          controllerAs: 'dialog',
                          size: 'lg',
                          resolve: {
                              templates: function(){
                                  return templates;
                              },
                              uiSettings: ['UiSettingsSrv', function(UiSettingsSrv) {
                                  return UiSettingsSrv.all();
                              }]
                          }
                      });

                      return modal.result;
                  })
                  .then(function(template) {

                      // Open case creation dialog
                      var modal = $uibModal.open({
                          templateUrl: 'views/partials/case/case.creation.html',
                          controller: 'CaseCreationCtrl',
                          size: 'lg',
                          resolve: {
                              template: template
                          }
                      });

                      return modal.result;
                  })
                  .then(function(createdCase) {
                      // Bulk merge the selected alerts into the created case
                      NotificationSrv.log('New case has been created', 'success');

                      return AlertingSrv.bulkMergeInto(alertIds, createdCase.id);
                  })
                  .then(function(response) {
                      if(alertIds.length === 1) {
                          NotificationSrv.log(alertIds.length + ' Alert has been merged into the newly created case.', 'success');
                      } else {
                          NotificationSrv.log(alertIds.length + ' Alert(s) have been merged into the newly created case.', 'success');
                      }

                      $rootScope.$broadcast('alert:event-imported');

                      $state.go('app.case.details', {
                          caseId: response.data.id
                      });
                  })
                  .catch(function(err) {
                      if(err && !_.isString(err)) {
                          NotificationSrv.error('AlertEventCtrl', err.data, err.status);
                      }
                  });

            };

            self.mergeInCase = function() {
                var caseModal = $uibModal.open({
                    templateUrl: 'views/partials/case/case.merge.html',
                    controller: 'CaseMergeModalCtrl',
                    controllerAs: 'dialog',
                    size: 'lg',
                    resolve: {
                        source: function() {
                            return self.event;
                        },
                        title: function() {
                            return 'Merge selected Alert(s)';
                        },
                        prompt: function() {
                            return 'the ' + self.selection.length + ' selected Alert(s)';
                        }
                    }
                });

                caseModal.result.then(function(selectedCase) {
                    return AlertingSrv.bulkMergeInto(_.pluck(self.selection, '_id'), selectedCase.id);
                })
                .then(function(response) {
                    $rootScope.$broadcast('alert:event-imported');

                    $state.go('app.case.details', {
                        caseId: response.data.id
                    });
                })
                .catch(function(err) {
                    if(err && !_.isString(err)) {
                        NotificationSrv.error('AlertEventCtrl', err.data, err.status);
                    }
                });
            };

            this.filter = function () {
                self.filtering.filter().then(this.applyFilters);
            };

            this.clearFilters = function () {
                this.filtering.clearFilters()
                    .then(self.search);
            };

            this.addFilter = function (field, value) {
                self.filtering.addFilter(field, value).then(this.applyFilters);
            };

            this.removeFilter = function (index) {
                self.filtering.removeFilter(index)
                    .then(self.search);
            };

            this.search = function () {
                self.load();
                self.filtering.storeContext();
            };
            this.addFilterValue = function (field, value) {
                this.filtering.addFilterValue(field, value);
                this.search();
            };

            this.filterByStatus = function(flag) {
                self.filtering.clearFilters()
                    .then(function(){
                        self.addFilterValue('imported', flag);
                    });
            };

            this.filterByNewAndUpdated = function() {
                self.filtering.clearFilters()
                    .then(function(){
                        // TODO nadouani: how to support updated alerts
                        self.addFilterValue('imported', true);
                    });
            };

            this.filterBySeverity = function(numericSev) {
                self.addFilterValue('severity', Severity.values[numericSev]);
            };

            this.filterBy = function(field, value) {
                self.filtering.clearFilters()
                    .then(function(){
                        self.addFilterValue(field, value);
                    });
            };

            this.sortBy = function(sort) {
                self.list.sort = sort;
                self.list.update();
                self.filtering.setSort(sort);
            };

            this.sortByField = function(field) {
                var context = this.filtering.context;
                var currentSort = Array.isArray(context.sort) ? context.sort[0] : context.sort;
                var sort = null;

                if(currentSort.substr(1) !== field) {
                    sort = ['+' + field];
                } else {
                    sort = [(currentSort === '+' + field) ? '-'+field : '+'+field];
                }

                self.list.sort = sort;
                self.list.update();
                self.filtering.setSort(sort);
            };
        });
})();
