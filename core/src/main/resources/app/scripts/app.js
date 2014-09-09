'use strict';

angular
  .module('moneyApp', [
    'ngResource',
    'ngRoute',
    'mgcrea.ngStrap'
  ])
  .config(function ($routeProvider) {
    $routeProvider
      .when('/', {
        templateUrl: 'views/main.html',
        controller: 'MainCtrl'
      })
      .when('/login', {
        templateUrl: 'views/login.html',
        controller: 'AuthCtrl'
      })
      .when('/register', {
        templateUrl: 'views/register.html',
        controller: 'AuthCtrl'
      })
      .when('/expenses/view', {
        templateUrl: 'views/expenses/view.html',
        controller: 'ExpensesViewCtrl'
      })
      .when('/expenses/viewrec', {
        templateUrl: 'views/expenses/viewRec.html',
        controller: 'ExpensesViewRecCtrl'
      })
      .when('/expenses/add/:rec', {
        templateUrl: 'views/expenses/edit.html',
        controller: 'ExpensesEditCtrl'
      })
      .when('/expenses/edit/:id', {
        templateUrl: 'views/expenses/edit.html',
        controller: 'ExpensesEditCtrl'
      })
      .when('/reports/list', {
        templateUrl: 'views/reports/list.html',
        controller: 'ReportsListCtrl'
      })
      .when('/reports/generate', {
        templateUrl: 'views/reports/generate.html',
        controller: 'ReportsGenCtrl'
      })
      .when('/reports/view/:id', {
        templateUrl: 'views/reports/view.html',
        controller: 'ReportsViewCtrl'
      })
      .when('/admin/users', {
        templateUrl: 'views/admin/viewUsers.html',
        controller: 'AdminUsersCtrl'
      })
      .when('/debts', {
        templateUrl: 'views/debts.html',
        controller: 'DebtsCtrl'
      })
      .otherwise({
        redirectTo: '/'
      });
  });
