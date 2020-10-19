(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('AlertEventCtrl', function($scope, $rootScope, $state, $uibModal, $uibModalInstance, ModalUtilsSrv, AuthenticationSrv, CustomFieldsSrv, CaseResolutionStatus, AlertingSrv, NotificationSrv, UiSettingsSrv, clipboard, event, templates, readonly) {
            var self = this;
            var eventId = event._id;

            self.eventId = event._id;
            self.readonly = readonly;
            self.templates = _.pluck(templates, 'name');
            self.CaseResolutionStatus = CaseResolutionStatus;
            self.event = event;
            self.canEdit = AuthenticationSrv.hasPermission('manageAlert');

            self.loading = true;

            self.pagination = {
                pageSize: 10,
                currentPage: 1,
                filter: '',
                data: []
            };

            self.similarityFilters = {};
            self.similaritySorts = ['-startDate', '-similarArtifactCount', '-similarIocCount', '-iocCount'];
            self.currentSimilarFilter = '';
            self.similarCasesStats = [];

            self.customFieldsCache = CustomFieldsSrv;

            self.counts = {
                observables: 0,
                similarCases: 0
            };

            self.hideEmptyCaseButton = UiSettingsSrv.uiHideEmptyCaseButton();

            self.updateObservableCount = function(count) {
                self.counts.observables = count;
            };

            self.updateSimilarCasesCount = function(count) {
                self.counts.similarCases = count;
            };

            self.getCustomFieldName = function(fieldDef) {
                return 'customFields.' + fieldDef.reference + '.' + fieldDef.type;
            };

            self.load = function() {
                AlertingSrv.get(eventId).then(function(data) {
                    self.event = data;
                    self.loading = false;
                    self.initSimilarCasesFilter(self.event.similarCases);

                    self.dataTypes = _.countBy(self.event.artifacts, function(attr) {
                        return attr.dataType;
                    });

                }, function(response) {
                  self.loading = false;
                  NotificationSrv.error('AlertEventCtrl', response.data, response.status);
                  $uibModalInstance.dismiss();
                });
            };

            self.updateField = function (fieldName, newValue) {
                var field = {};
                field[fieldName] = newValue;

                return AlertingSrv.update(self.event._id, field)
                  .then(function() {
                      NotificationSrv.log('Alert updated successfully', 'success');
                  })
                  .catch(function (response) {
                      NotificationSrv.error('AlertEventCtrl', response.data, response.status);
                  });
            };

            self.import = function() {
                self.loading = true;
                AlertingSrv.create(self.event._id, {
                    caseTemplate: self.event.caseTemplate
                }).then(function(response) {
                    $uibModalInstance.dismiss();

                    $rootScope.$broadcast('alert:event-imported');

                    $state.go('app.case.details', {
                        caseId: response.data.id
                    });
                }, function(response) {
                    self.loading = false;
                    NotificationSrv.error('AlertEventCtrl', response.data, response.status);
                });
            };

            self.mergeIntoCase = function(caseId) {
                self.loading = true;
                AlertingSrv.mergeInto(self.event._id, caseId)
                    .then(function(response) {
                        $uibModalInstance.dismiss();

                        $rootScope.$broadcast('alert:event-imported');

                        $state.go('app.case.details', {
                            caseId: response.data.id
                        });
                    }, function(response) {
                        self.loading = false;
                        NotificationSrv.error('AlertEventCtrl', response.data, response.status);
                    });
            };

            self.merge = function() {
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
                            return 'Merge Alert: ' + self.event.title;
                        },
                        prompt: function() {
                            return self.event.title;
                        }
                    }
                });

                caseModal.result.then(function(selectedCase) {
                    self.mergeIntoCase(selectedCase._id);
                }).catch(function(err) {
                    if(err && !_.isString(err)) {
                        NotificationSrv.error('AlertEventCtrl', err.data, err.status);
                    }
                });
            };

            this.follow = function() {
                var fn = angular.noop;

                if (self.event.follow === true) {
                    fn = AlertingSrv.unfollow;
                } else {
                    fn = AlertingSrv.follow;
                }

                fn(self.event._id).then(function() {
                    $uibModalInstance.close();
                }).catch(function(response) {
                    NotificationSrv.error('AlertEventCtrl', response.data, response.status);
                });
            };

            this.delete = function() {
                ModalUtilsSrv.confirm('Remove Alert', 'Are you sure you want to delete this Alert?', {
                    okText: 'Yes, remove it',
                    flavor: 'danger'
                }).then(function() {
                    AlertingSrv.forceRemove(self.event._id)
                    .then(function() {
                        $uibModalInstance.close();
                        NotificationSrv.log('Alert has been permanently deleted', 'success');
                    })
                    .catch(function(response) {
                        NotificationSrv.error('AlertEventCtrl', response.data, response.status);
                    });
                });

            };

            this.canMarkAsRead = AlertingSrv.canMarkAsRead;
            this.canMarkAsUnread = AlertingSrv.canMarkAsUnread;

            this.markAsRead = function() {
                var fn = angular.noop;

                if(this.canMarkAsRead(this.event)) {
                    fn = AlertingSrv.markAsRead;
                } else {
                    fn = AlertingSrv.markAsUnread;
                }

                fn(this.event._id).then(function( /*data*/ ) {
                    $uibModalInstance.close();
                }, function(response) {
                    NotificationSrv.error('AlertEventCtrl', response.data, response.status);
                });
            };

            self.cancel = function() {
                $uibModalInstance.dismiss();
            };

            self.initSimilarCasesFilter = function(data) {
                var stats = {
                    'Open': 0
                };

                // Init the stats object
                _.each(_.without(_.keys(CaseResolutionStatus), 'Duplicated'), function(key) {
                    stats[key] = 0;
                });

                _.each(data, function(item) {
                    if(item.status === 'Open') {
                        stats[item.status] = stats[item.status] + 1;
                    } else {
                        stats[item.resolutionStatus] = stats[item.resolutionStatus] + 1;
                    }
                });

                var result = [];
                _.each(_.keys(stats), function(key) {
                    result.push({
                        key: key,
                        value: stats[key]
                    });
                });

                self.similarCasesStats = result;
            };

            self.filterSimilarCases = function(filter) {
                self.currentSimilarFilter = filter;
                if(filter === '') {
                    self.similarityFilters = {};
                } else if(filter === 'Open') {
                    self.similarityFilters = {
                        status: filter
                    };
                } else {
                    self.similarityFilters = {
                        resolutionStatus: filter
                    };
                }
            };

            self.copyId = function(id) {
                clipboard.copyText(id);
            };

            this.$onInit = function() {
                self.load();
            };
        });
})();
