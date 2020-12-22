/**
 * Controller for About TheHive modal page
 */
(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AddTaskLogModalCtrl', function($rootScope, $scope, $uibModalInstance, TaskLogSrv, NotificationSrv, task, config) {
            var self = this;

            this.task = task;
            this.config = config;

            this.close = function() {
                $uibModalInstance.close();
            };

            this.cancel = function() {
                $rootScope.markdownEditorObjects.newLog.hidePreview();

                $uibModalInstance.dismiss();
            };

            this.addLog = function() {
                // this.close();
                if (this.state.attachmentCollapsed || !this.data.attachment) {
                    delete this.data.attachment;
                }

                TaskLogSrv.save({
                    'taskId': self.task._id
                }, self.data, function () {
                    // if(self.task.status === 'Waiting') {
                    //     // Reload the task
                    //     $scope.reloadTask();
                    // }
                    //
                    delete self.data.attachment;
                    self.state.attachmentCollapsed = true;
                    self.data.message = '';

                    $rootScope.markdownEditorObjects.newLog.hidePreview();
                    // $scope.adding = false;
                    // removeAllFiles is added by dropzone directive as control
                    self.state.removeAllFiles();

                    self.state.loading = false;

                    self.close();
                }, function (response) {
                    NotificationSrv.error('Add Task Log', response.data, response.status);
                    self.state.loading = false;
                });

            };

            this.$onInit = function() {
                this.markdownEditorOptions = {
                    iconlibrary: 'fa',
                    addExtraButtons: true,
                    resize: 'vertical'
                };

                this.data = {
                    message: null,
                    attachment: null
                };

                this.state = {
                    attachmentCollapsed: true,
                    loading: false
                };

                $scope.$broadcast('beforeNewTaskLogShow');
            };
        }
    );
})();
